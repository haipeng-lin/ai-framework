//package org.happyhai.springai.alibaba.voice.diagnostic;
//
//import org.springframework.ai.audio.transcription.TranscriptionModel;
//import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.context.annotation.Primary;
//
///**
// * 把 Spring AI 自动装配的 OpenAiAudioTranscriptionModel 包一层日志装饰器。
// *
// * <p>{@code @Primary} 保证 {@code @Autowired TranscriptionModel}（比如
// * {@link org.happyhai.springai.alibaba.voice.service.AudioTransactionService}）
// * 注进来的是日志装饰后的版本,而不是裸的 OpenAi 实现。</p>
// */
//@Configuration
//public class TranscriptionModelLoggingConfig {
//
//    @Bean
//    @Primary
//    public TranscriptionModel transcriptionModel(OpenAiAudioTranscriptionModel delegate) {
//        return new LoggingTranscriptionModel(delegate);
//    }
//}
