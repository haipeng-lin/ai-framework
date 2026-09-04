package org.happyhai.springai.alibaba.comprehensive.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.happyhai.springai.alibaba.comprehensive.domain.TraceInfo;
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
    private static final String TRACE_KEY_PREFIX = "comp:trace:";
    private static final long TRACE_EXPIRE_SECONDS = 3600;

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public TraceInfoService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
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
            logger.info("TraceInfo saved - traceId: {}", traceId);
        } catch (Exception e) {
            logger.error("Failed to save TraceInfo", e);
            throw new RuntimeException("Failed to save trace info", e);
        }
        return traceId;
    }

    public Optional<TraceInfo> getTraceInfo(String traceId) {
        String key = TRACE_KEY_PREFIX + traceId;
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                logger.debug("TraceInfo not found - traceId: {}", traceId);
                return Optional.empty();
            }
            TraceInfo traceInfo = objectMapper.convertValue(value, TraceInfo.class);
            return Optional.of(traceInfo);
        } catch (Exception e) {
            logger.error("Failed to get TraceInfo - traceId: {}", traceId, e);
            return Optional.empty();
        }
    }

    public boolean updateTraceInfo(TraceInfo traceInfo) {
        String traceId = traceInfo.getTraceId();
        String key = TRACE_KEY_PREFIX + traceId;
        try {
            traceInfo.setUpdatedAt(java.time.LocalDateTime.now());
            redisTemplate.opsForValue().set(key, traceInfo, TRACE_EXPIRE_SECONDS, TimeUnit.SECONDS);
            logger.info("TraceInfo updated - traceId: {}", traceId);
            return true;
        } catch (Exception e) {
            logger.error("Failed to update TraceInfo - traceId: {}", traceId, e);
            return false;
        }
    }

    public boolean updateStatus(String traceId, TraceInfo.Status status) {
        Optional<TraceInfo> optionalTraceInfo = getTraceInfo(traceId);
        if (optionalTraceInfo.isEmpty()) {
            return false;
        }
        TraceInfo traceInfo = optionalTraceInfo.get();
        traceInfo.setStatus(status.name());
        traceInfo.setUpdatedAt(java.time.LocalDateTime.now());
        return updateTraceInfo(traceInfo);
    }

    public boolean updateLightControl(String traceId, String deviceId, String deviceName, String command, String productCode) {
        Optional<TraceInfo> optionalTraceInfo = getTraceInfo(traceId);
        if (optionalTraceInfo.isEmpty()) {
            logger.warn("Cannot update light control - TraceInfo not found - traceId: {}", traceId);
            return false;
        }
        TraceInfo traceInfo = optionalTraceInfo.get();
        traceInfo.setDeviceId(deviceId);
        traceInfo.setDeviceName(deviceName);
        traceInfo.setAction(command);
        traceInfo.setCommand(productCode);
        traceInfo.setStatus(TraceInfo.Status.PENDING_CONFIRMATION.name());
        traceInfo.setUpdatedAt(java.time.LocalDateTime.now());
        return updateTraceInfo(traceInfo);
    }

    public boolean deleteTraceInfo(String traceId) {
        String key = TRACE_KEY_PREFIX + traceId;
        try {
            Boolean deleted = redisTemplate.delete(key);
            return Boolean.TRUE.equals(deleted);
        } catch (Exception e) {
            logger.error("Failed to delete TraceInfo - traceId: {}", traceId, e);
            return false;
        }
    }
}
