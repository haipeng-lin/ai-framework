package org.happyhai.springai.alibaba.voice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.audio.transcription.AudioTranscriptionOptions;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 音频内容识别服务：单线程 + 智能并行分段（参考 T05-voice-chat-robot）。
 */
@Service
public class AudioTransactionService {

    private static final Logger log = LoggerFactory.getLogger(AudioTransactionService.class);

    @Autowired
    private TranscriptionModel transcriptionModel;

    @Autowired
    private AudioSegmentationService segmentationService;

    public String audioTransaction(MultipartFile file) throws IOException {
        return audioTransactionSingle(file);
    }

    /**
     * 并行处理音频文件（智能分割）。
     */
    public String audioTransactionParallel(MultipartFile file, boolean useParallel) throws IOException {
        if (!useParallel) {
            return audioTransactionSingle(file);
        }

        long startTime = System.currentTimeMillis();
        log.info("开始并行处理音频文件: {}", file.getOriginalFilename());

        try {
            List<AudioSegmentationService.AudioSegment> segments = segmentationService.smartSegment(file);

            if (segments.size() <= 1) {
                log.info("音频文件较小，直接处理");
                return audioTransactionSingle(file);
            }

            List<CompletableFuture<String>> futures = segmentationService.processSegmentsParallel(
                    segments,
                    this::transcribeSegment);

            List<String> results = segmentationService.collectResults(futures);
            String finalResult = mergeTranscriptionResults(results);

            long endTime = System.currentTimeMillis();
            log.info("并行处理完成，总耗时: {}ms，片段数: {}，结果长度: {}字符",
                    endTime - startTime, segments.size(), finalResult.length());
            return finalResult;

        } catch (Exception e) {
            log.error("并行处理音频文件失败，回退到单线程处理", e);
            return audioTransactionSingle(file);
        }
    }


    /**
     * 单线程音频转录
     */
    private String audioTransactionSingle(MultipartFile file) throws IOException {
        long startTime = System.currentTimeMillis();
        log.info("开始单线程处理音频文件: {}", file.getOriginalFilename());

        // 1. 设置正确的 OpenAiAudioTranscriptionOptions
        // 建议：如果传 JSON 报 500，可以暂时不强制设置 responseFormat，或者使用 OpenAiAudioApi.TranscriptResponseFormat.JSON
//        AudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
////                .responseFormat(OpenAiAudioApi.TranscriptResponseFormat.JSON)
//                .model("FunAudioLLM/SenseVoiceSmall")
//                .build();

        // 2. 使用封装好的 NamedByteArrayResource
        byte[] bytes = file.getBytes();
        Resource resource = new NamedByteArrayResource(bytes, file.getOriginalFilename());

        AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(resource);
        AudioTranscriptionResponse response = transcriptionModel.call(prompt);

        long endTime = System.currentTimeMillis();
        log.info("单线程处理完成，耗时: {}ms", endTime - startTime);

        return response.getResult().getOutput();
    }

    /**
     * 转录单个音频片段
     */
    private String transcribeSegment(AudioSegmentationService.AudioSegment segment) {
        try {
            AudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
                    .responseFormat(OpenAiAudioApi.TranscriptResponseFormat.JSON)
                    .model("FunAudioLLM/SenseVoiceSmall")
                    .build();

            // 保持带扩展名的文件名，以便 Spring 正确推断 Content-Type (如 audio/mpeg 或 audio/mp3)
            String segmentName = "segment_" + segment.getIndex() + ".mp3";
            Resource resource = new NamedByteArrayResource(segment.getData(), segmentName);

            AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(resource, options);
            AudioTranscriptionResponse response = transcriptionModel.call(prompt);

            String result = response.getResult().getOutput();
            log.debug("片段 {} 转录完成，结果长度: {} 字符", segment.getIndex(), result.length());
            return result;
        } catch (Exception e) {
            log.error("转录片段 {} 失败", segment.getIndex(), e);
            return "";
        }
    }

    /**
     * 合并多个转录结果。
     */
    private String mergeTranscriptionResults(List<String> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }

        List<String> validResults = results.stream()
                .filter(result -> result != null && !result.trim().isEmpty())
                .collect(Collectors.toList());

        if (validResults.isEmpty()) {
            return "";
        }

        String mergedResult = String.join(" ", validResults);
        mergedResult = mergedResult.replaceAll("\\s+", " ").trim();

        log.info("合并转录结果: 共 {} 个有效片段，合并后长度: {} 字符",
                validResults.size(), mergedResult.length());
        return mergedResult;
    }

    /**
     * 修复 ByteArrayResource 在 RestTemplate/RestClient multipart 提交时丢失 MIME/Filename 的问题
     */
    public class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        public NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return this.filename;
        }
    }
}
