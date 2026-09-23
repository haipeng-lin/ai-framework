//package org.happyhai.springai.alibaba.voice.diagnostic;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.ai.audio.transcription.AudioTranscriptionOptions;
//import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
//import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
//import org.springframework.ai.audio.transcription.TranscriptionModel;
//import org.springframework.core.io.Resource;
//
//import java.lang.reflect.Method;
//import java.util.Arrays;
//import java.util.function.Function;
//
///**
// * TranscriptionModel 装饰器：调真正的模型前后打印入参/出参。
// *
// * <p>为什么不用 SimpleLoggerAdvisor：它是 ChatClient 的 Advisor SPI,
// * 只在 ChatClient 调用链里生效。OpenAiAudioTranscriptionModel 走的是
// * TranscriptionModel.call,不会经过 ChatClient,Advisor 拦不到。</p>
// *
// * <p>对 options 用反射展开所有 getter(它没重写 toString),
// * spring-ai 1.1.x / 2.0.0-M2 字段集合不完全一致,逐个写 getter 会漏,
// * 这样未来字段变化也能跟着打。</p>
// */
//public class LoggingTranscriptionModel implements TranscriptionModel {
//
//    private static final Logger log = LoggerFactory.getLogger(LoggingTranscriptionModel.class);
//
//    private final TranscriptionModel delegate;
//
//    public LoggingTranscriptionModel(TranscriptionModel delegate) {
//        this.delegate = delegate;
//    }
//
//    @Override
//    public AudioTranscriptionResponse call(AudioTranscriptionPrompt prompt) {
//        logBeforeCall(prompt);
//        long start = System.currentTimeMillis();
//        try {
//            AudioTranscriptionResponse response = delegate.call(prompt);
//            logAfterCall(response, System.currentTimeMillis() - start);
//            return response;
//        } catch (Exception e) {
//            log.error("<<< [AudioModel] 调用异常 ({} ms): {}",
//                    System.currentTimeMillis() - start, e.getMessage());
//            throw e;
//        }
//    }
//
//    private void logBeforeCall(AudioTranscriptionPrompt prompt) {
//        log.info(">>> [AudioModel] 请求入参:");
//
//        AudioTranscriptionOptions opts = prompt.getOptions();
//        if (opts == null) {
//            log.info("    options = (null)");
//        } else {
//            log.info("    options.class = {}", opts.getClass().getName());
//            for (Method m : opts.getClass().getMethods()) {
//                if (!isSimpleGetter(m)) continue;
//                Object value = safeInvoke(opts, m);
//                log.info("    {} = {}", m.getName(), formatValue(value));
//            }
//        }
//
//        Resource r = prompt.getInstructions();
//        log.info("    filename   = {}", safe(r, Resource::getFilename));
//        log.info("    contentLen = {} bytes", safeLen(r));
//        log.info("    exists     = {}", safeExists(r));
//    }
//
//    private void logAfterCall(AudioTranscriptionResponse response, long costMs) {
//        log.info("<<< [AudioModel] 响应出参 ({} ms):", costMs);
//        log.info("    result    = {}", response.getResult().getOutput());
//        log.info("    metadata  = {}", response.getResult().getMetadata());
//    }
//
//    private static boolean isSimpleGetter(Method m) {
//        if (m.getParameterCount() != 0) return false;
//        if (m.getReturnType() == void.class) return false;
//        String name = m.getName();
//        if (name.startsWith("get") && name.length() > 3 && Character.isUpperCase(name.charAt(3))) {
//            return true;
//        }
//        if (name.startsWith("is") && name.length() > 2 && Character.isUpperCase(name.charAt(2))) {
//            Class<?> rt = m.getReturnType();
//            return rt == boolean.class || rt == Boolean.class;
//        }
//        return false;
//    }
//
//    private static Object safeInvoke(Object target, Method m) {
//        try {
//            return m.invoke(target);
//        } catch (Exception e) {
//            return "(invoke error: " + e.getClass().getSimpleName() + ")";
//        }
//    }
//
//    private static String formatValue(Object value) {
//        if (value == null) return "(null)";
//        Class<?> cls = value.getClass();
//        if (cls.isArray()) {
//            Class<?> comp = cls.getComponentType();
//            if (comp == int.class) return Arrays.toString((int[]) value);
//            if (comp == long.class) return Arrays.toString((long[]) value);
//            if (comp == double.class) return Arrays.toString((double[]) value);
//            if (comp == float.class) return Arrays.toString((float[]) value);
//            if (comp == boolean.class) return Arrays.toString((boolean[]) value);
//            if (comp == byte.class) return Arrays.toString((byte[]) value);
//            if (comp == short.class) return Arrays.toString((short[]) value);
//            if (comp == char.class) return Arrays.toString((char[]) value);
//            return Arrays.toString((Object[]) value);
//        }
//        return value.toString();
//    }
//
//    private static String safe(Resource r, Function<Resource, String> getter) {
//        try {
//            return r == null ? "(null)" : getter.apply(r);
//        } catch (Exception e) {
//            return "(error: " + e.getClass().getSimpleName() + ")";
//        }
//    }
//
//    private static long safeLen(Resource r) {
//        try {
//            return r == null ? -1 : r.contentLength();
//        } catch (Exception e) {
//            return -1;
//        }
//    }
//
//    private static boolean safeExists(Resource r) {
//        try {
//            return r != null && r.exists();
//        } catch (Exception e) {
//            return false;
//        }
//    }
//}
