package org.happyhai.springai.alibaba.controller;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.happyhai.springai.alibaba.service.AudioTranscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

/**
 * 接收上传的音频文件，立刻返回一个任务 id，
 * 然后把识别结果和流式翻译结果通过 SSE 推到浏览器。
 *
 * 流程：
 *   1. POST /auto/uploadAudio                -> { "taskId": "..." }
 *   2. GET  /auto/getResultStream/{taskId}   -> SseEmitter（事件：transcription、translation、end、error）
 */
@RestController
@RequestMapping("/auto")
public class AutoTranslateController {

    private static final Logger log = LoggerFactory.getLogger(AutoTranslateController.class);

    private static final String TRANS_SYSTEM_PROMPT = """
            你是一名专业同声传译。
            请将下面的文本翻译成 {lan}。
            要求：
            - 只输出译文本身，不要任何解释、注释或引号；
            - 保留原文的语气与风格；
            - 如果原文已经是 {lan}，请原样返回。
            待翻译文本：
            {content}
            """;

    private final AudioTranscriptionService audioTranscriptionService;
    private final ChatClient chatClient;

    /** Holds every uploaded task until its SSE connection is established and processing finishes. */
    private final Map<String, TranslationTask> taskMap = new ConcurrentHashMap<>();
    /** SSE emitters keyed by taskId, populated when the browser opens the stream. */
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public AutoTranslateController(AudioTranscriptionService audioTranscriptionService, ChatClient.Builder chatClientBuilder) {
        this.audioTranscriptionService = audioTranscriptionService;
        this.chatClient = chatClientBuilder.build();
    }

    @PostMapping(path = "/uploadAudio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> uploadAudio(@RequestParam("file") MultipartFile file,
                                           @RequestParam(value = "targetLanguage", defaultValue = "英语") String targetLanguage) {
        // 在请求线程内一次性读完字节缓存下来，避免 MultipartFile 跨线程失效
        byte[] audioBytes;
        try {
            audioBytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("读取上传文件失败", e);
        }
        if (audioBytes.length == 0) {
            throw new IllegalArgumentException("上传的音频文件为空");
        }

        String taskId = UUID.randomUUID().toString();
        String filename = file.getOriginalFilename();
        taskMap.put(taskId, new TranslationTask(taskId, audioBytes, filename, targetLanguage));
        log.info("收到音频 '{}' ({} 字节)，taskId={}，目标语言={}", filename, audioBytes.length, taskId, targetLanguage);

        new Thread(() -> processTranslationTask(taskId), "translate-" + taskId).start();
        return Map.of("taskId", taskId);
    }

    @GetMapping(path = "/getResultStream/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter getResultStream(@PathVariable String taskId) {
        // 0L = never time out server-side; client controls the connection lifetime.
        SseEmitter emitter = new SseEmitter(0L);
        emitter.onCompletion(() -> cleanup(taskId, "completed"));
        emitter.onTimeout(() -> cleanup(taskId, "timed-out"));
        emitter.onError(t -> cleanup(taskId, "error:" + t.getMessage()));

        emitters.put(taskId, emitter);
        log.info("SSE 已建立连接，taskId={}，当前连接数={}", taskId, emitters.size());
        return emitter;
    }

    private void processTranslationTask(String taskId) {
        TranslationTask task = taskMap.get(taskId);
        if (task == null) {
            log.warn("任务 {} 已不存在", taskId);
            return;
        }

        // The browser needs a moment to open the SSE connection after uploadAudio returns.
        SseEmitter emitter = waitForEmitter(taskId);
        if (emitter == null) {
            log.warn("10 秒内没有浏览器连上 SSE，放弃任务 taskId={}", taskId);
            taskMap.remove(taskId);
            return;
        }

        try {
            String transcription = audioTranscriptionService.transcribe(task.audioBytes(), task.filename());
            send(emitter, "transcription", transcription);

            Prompt prompt = new PromptTemplate(TRANS_SYSTEM_PROMPT).create(Map.of(
                    "lan", task.targetLanguage(),
                    "content", transcription));

            Flux<String> stream = chatClient.prompt(prompt).stream().content();
            StringBuilder accumulated = new StringBuilder();
            // doOnNext fires the SSE push for each chunk; blockLast parks the worker thread
            // until the model finishes streaming so we can send the "end" event afterwards.
            stream.doOnNext(chunk -> {
                accumulated.append(chunk);
                sendQuietly(emitter, "translation", chunk);
            }).blockLast();
            log.info("任务 {} 翻译完成，共 {} 个字符", taskId, accumulated.length());
            send(emitter, "end", "true");
        } catch (IllegalArgumentException e) {
            log.error("音频参数无效，taskId={}：{}", taskId, e.getMessage());
            sendQuietly(emitter, "error", e.getMessage());
        } catch (Exception e) {
            log.error("翻译失败，taskId={}", taskId, e);
            sendQuietly(emitter, "error", "转写失败：" + e.getMessage());
        } finally {
            cleanup(taskId, "finished");
        }
    }

    private SseEmitter waitForEmitter(String taskId) {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (System.currentTimeMillis() < deadline) {
            SseEmitter emitter = emitters.get(taskId);
            if (emitter != null) {
                return emitter;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private void send(SseEmitter emitter, String name, String data) throws IOException {
        emitter.send(SseEmitter.event().name(name).data(data));
    }

    private void sendQuietly(SseEmitter emitter, String name, String data) {
        try {
            send(emitter, name, data);
        } catch (IOException e) {
            log.debug("SSE 事件 {} 推送失败：{}", name, e.getMessage());
        }
    }

    private void cleanup(String taskId, String reason) {
        SseEmitter emitter = emitters.remove(taskId);
        taskMap.remove(taskId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // already completed
            }
        }
        log.info("已清理任务 taskId={}（{}）", taskId, reason);
    }
}
