package org.happyhai.springai.alibaba.comprehensive.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * SSE 流式会话的协调服务。
 * <p>
 * chat-stream 端点在 ACTION_CONFIRMATION 事件后阻塞等待，
 * confirm 端点批准后通过此类唤醒对应的 SSE 流。
 */
@Service
public class LightSseService {

    private static final Logger logger = LoggerFactory.getLogger(LightSseService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** key = traceId, value = CountDownLatch，SSE 端点在 ACTION_CONFIRMATION 后等待 */
    private static final ConcurrentHashMap<String, CountDownLatch> sseLatches = new ConcurrentHashMap<>();

    /** key = traceId, value = SSE END 事件的消息内容 */
    private static final ConcurrentHashMap<String, String> sseEndMessages = new ConcurrentHashMap<>();

    public static ObjectMapper objectMapper() {
        return objectMapper;
    }

    /**
     * 注册一个 trace 的 latch。
     * @return 注册前该 trace 是否已存在（不应存在）
     */
    public boolean registerTrace(String traceId) {
        return sseLatches.putIfAbsent(traceId, new CountDownLatch(1)) == null;
    }

    /**
     * 注销 trace，移除所有协调状态。
     */
    public void unregisterTrace(String traceId) {
        sseLatches.remove(traceId);
        sseEndMessages.remove(traceId);
    }

    /**
     * SSE 端点调用：等待 confirm 端点批准或拒绝。
     * @param traceId  trace id
     * @param timeoutMs 最大等待毫秒数
     * @return confirm 端点写入的消息，若超时或 traceId 不存在返回 null
     */
    public String awaitConfirmation(String traceId, long timeoutMs) {
        CountDownLatch latch = sseLatches.get(traceId);
        if (latch == null) {
            logger.warn("awaitConfirmation: traceId={} 不存在 latch", traceId);
            return null;
        }
        try {
            boolean triggered = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            String msg = sseEndMessages.remove(traceId);
            return triggered ? (msg != null ? msg : "操作已完成") : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * confirm 端点调用：批准/拒绝后写入消息并唤醒 latch。
     * @param traceId  trace id
     * @param endMessage END 事件的消息
     * @return 是否成功唤醒（traceId 对应的 latch 是否存在）
     */
    public boolean triggerConfirmation(String traceId, String endMessage) {
        CountDownLatch latch = sseLatches.get(traceId);
        if (latch == null) {
            logger.debug("triggerConfirmation: traceId={} 不存在 latch（非 SSE 调用）", traceId);
            return false;
        }
        sseEndMessages.put(traceId, endMessage);
        latch.countDown();
        logger.info("已唤醒 SSE latch，traceId: {}", traceId);
        return true;
    }

    public boolean hasActiveLatch(String traceId) {
        return sseLatches.containsKey(traceId);
    }
}