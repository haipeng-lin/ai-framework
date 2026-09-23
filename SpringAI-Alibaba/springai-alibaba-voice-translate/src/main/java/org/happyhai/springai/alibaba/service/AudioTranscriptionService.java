package org.happyhai.springai.alibaba.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.audio.transcription.AudioTranscriptionOptions;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * 通过 Spring AI 的 OpenAI 兼容接口调用 SiliconFlow 上的 SenseVoiceSmall 模型，
 * 把音频字节数组转写成纯文本。
 */
@Service
public class AudioTranscriptionService {

    private static final Logger log = LoggerFactory.getLogger(AudioTranscriptionService.class);

    private static final String DEFAULT_MODEL = "XingChenAGI/XingChenASR-V3.2";

    private final TranscriptionModel transcriptionModel;

    public AudioTranscriptionService(TranscriptionModel transcriptionModel) {
        this.transcriptionModel = transcriptionModel;
    }

    /**
     * @param audioBytes 音频原始字节（由调用方在请求线程内读取完毕，避免 MultipartFile 跨线程失效）
     * @param filename   用于让模型识别文件类型，建议保留原始文件名及扩展名
     */
    public String transcribe(byte[] audioBytes, String filename) {
        if (audioBytes == null || audioBytes.length == 0) {
            throw new IllegalArgumentException("音频字节为空");
        }

        AudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
                .model(DEFAULT_MODEL)
                .build();

        String safeName = (filename == null || filename.isBlank()) ? "audio" : filename;
        Resource resource = new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return safeName;
            }
        };

        long start = System.currentTimeMillis();
        AudioTranscriptionResponse response = transcriptionModel.call(new AudioTranscriptionPrompt(resource, options));
        String text = response.getResult().getOutput();
        log.info("已转写 '{}'，耗时 {} ms（共 {} 字）", safeName,
                System.currentTimeMillis() - start, text == null ? 0 : text.length());
        return text;
    }
}
