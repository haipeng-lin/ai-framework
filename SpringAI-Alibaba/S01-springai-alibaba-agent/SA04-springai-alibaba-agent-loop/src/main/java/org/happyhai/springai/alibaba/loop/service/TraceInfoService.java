package org.happyhai.springai.alibaba.loop.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.happyhai.springai.alibaba.loop.domain.TraceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class TraceInfoService {

    private static final Logger logger = LoggerFactory.getLogger(TraceInfoService.class);
    private static final String TRACE_KEY_PREFIX = "hitl:trace:";
    private static final long TRACE_EXPIRE_SECONDS = 3600;

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public TraceInfoService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
    }

    public String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public String saveTraceInfo(TraceInfo traceInfo) {
        String traceId = traceInfo.getTraceId();
        if (traceId == null || traceId.isEmpty()) {
            traceId = generateTraceId();
            traceInfo.setTraceId(traceId);
        }
        
        String key = TRACE_KEY_PREFIX + traceId;
        try {
            redisTemplate.opsForValue().set(key, traceInfo, TRACE_EXPIRE_SECONDS, TimeUnit.SECONDS);
            logger.info("TraceInfo saved to Redis - key: {}, traceId: {}", key, traceId);
        } catch (Exception e) {
            logger.error("Failed to save TraceInfo to Redis", e);
            throw new RuntimeException("Failed to save trace info", e);
        }
        return traceId;
    }

    public Optional<TraceInfo> getTraceInfo(String traceId) {
        String key = TRACE_KEY_PREFIX + traceId;
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                logger.debug("TraceInfo not found in Redis - traceId: {}", traceId);
                return Optional.empty();
            }
            
            TraceInfo traceInfo;
            if (value instanceof TraceInfo) {
                traceInfo = (TraceInfo) value;
            } else {
                traceInfo = objectMapper.convertValue(value, TraceInfo.class);
            }
            logger.debug("TraceInfo retrieved from Redis - traceId: {}, info: {}", traceId, traceInfo);
            return Optional.of(traceInfo);
        } catch (Exception e) {
            logger.error("Failed to get TraceInfo from Redis - traceId: {}", traceId, e);
            return Optional.empty();
        }
    }

    public boolean updateTraceStatus(String traceId, TraceInfo.Status status) {
        Optional<TraceInfo> optionalTraceInfo = getTraceInfo(traceId);
        if (optionalTraceInfo.isEmpty()) {
            logger.warn("Cannot update status - TraceInfo not found - traceId: {}", traceId);
            return false;
        }

        TraceInfo traceInfo = optionalTraceInfo.get();
        traceInfo.setStatus(status.name());
        traceInfo.setUpdatedAt(java.time.LocalDateTime.now());
        
        String key = TRACE_KEY_PREFIX + traceId;
        try {
            redisTemplate.opsForValue().set(key, traceInfo, TRACE_EXPIRE_SECONDS, TimeUnit.SECONDS);
            logger.info("TraceInfo status updated - traceId: {}, newStatus: {}", traceId, status);
            return true;
        } catch (Exception e) {
            logger.error("Failed to update TraceInfo status in Redis - traceId: {}", traceId, e);
            return false;
        }
    }

    public boolean updateLightParams(String traceId, String lightName, String preset, Integer brightness) {
        Optional<TraceInfo> optionalTraceInfo = getTraceInfo(traceId);
        if (optionalTraceInfo.isEmpty()) {
            logger.warn("Cannot update params - TraceInfo not found - traceId: {}", traceId);
            return false;
        }

        TraceInfo traceInfo = optionalTraceInfo.get();
        traceInfo.setLightName(lightName);
        traceInfo.setPreset(preset);
        traceInfo.setBrightness(brightness);
        traceInfo.setStatus(TraceInfo.Status.PENDING.name());
        traceInfo.setUpdatedAt(java.time.LocalDateTime.now());
        
        String key = TRACE_KEY_PREFIX + traceId;
        try {
            redisTemplate.opsForValue().set(key, traceInfo, TRACE_EXPIRE_SECONDS, TimeUnit.SECONDS);
            logger.info("TraceInfo light params updated - traceId: {}, lightName: {}, preset: {}, brightness: {}%",
                    traceId, lightName, preset, brightness);
            return true;
        } catch (Exception e) {
            logger.error("Failed to update TraceInfo light params in Redis - traceId: {}", traceId, e);
            return false;
        }
    }

    public boolean deleteTraceInfo(String traceId) {
        String key = TRACE_KEY_PREFIX + traceId;
        try {
            Boolean deleted = redisTemplate.delete(key);
            logger.info("TraceInfo deleted from Redis - traceId: {}, deleted: {}", traceId, deleted);
            return Boolean.TRUE.equals(deleted);
        } catch (Exception e) {
            logger.error("Failed to delete TraceInfo from Redis - traceId: {}", traceId, e);
            return false;
        }
    }

    public void updateThreadId(String traceId, String threadId) {
        Optional<TraceInfo> optionalTraceInfo = getTraceInfo(traceId);
        if (optionalTraceInfo.isPresent()) {
            TraceInfo traceInfo = optionalTraceInfo.get();
            traceInfo.setThreadId(threadId);
            traceInfo.setUpdatedAt(java.time.LocalDateTime.now());
            
            String key = TRACE_KEY_PREFIX + traceId;
            redisTemplate.opsForValue().set(key, traceInfo, TRACE_EXPIRE_SECONDS, TimeUnit.SECONDS);
            logger.info("ThreadId updated - traceId: {}, threadId: {}", traceId, threadId);
        }
    }
}
