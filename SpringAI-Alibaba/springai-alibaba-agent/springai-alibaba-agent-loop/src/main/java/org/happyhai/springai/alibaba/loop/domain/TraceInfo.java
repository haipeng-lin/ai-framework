package org.happyhai.springai.alibaba.loop.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

public class TraceInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private String traceId;
    private String lightName;
    private String preset;
    private Integer brightness;
    private String status;
    private String threadId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public TraceInfo() {
    }

    public TraceInfo(String traceId, String lightName, String preset, Integer brightness, String status, String threadId) {
        this.traceId = traceId;
        this.lightName = lightName;
        this.preset = preset;
        this.brightness = brightness;
        this.status = status;
        this.threadId = threadId;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getLightName() {
        return lightName;
    }

    public void setLightName(String lightName) {
        this.lightName = lightName;
    }

    public String getPreset() {
        return preset;
    }

    public void setPreset(String preset) {
        this.preset = preset;
    }

    public Integer getBrightness() {
        return brightness;
    }

    public void setBrightness(Integer brightness) {
        this.brightness = brightness;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED,
        EXPIRED
    }

    @Override
    public String toString() {
        return "TraceInfo{" +
                "traceId='" + traceId + '\'' +
                ", lightName='" + lightName + '\'' +
                ", preset='" + preset + '\'' +
                ", brightness=" + brightness +
                ", status='" + status + '\'' +
                ", threadId='" + threadId + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
