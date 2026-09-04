package org.happyhai.springai.alibaba.comprehensive.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

public class TraceInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private String traceId;
    private String userId;
    private String deviceId;
    private String deviceName;
    private String action;
    private String command;
    private String status;
    private String threadId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public TraceInfo() {
    }

    public TraceInfo(String traceId, String userId, String status, String threadId) {
        this.traceId = traceId;
        this.userId = userId;
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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
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
        PENDING_DEVICE_SELECTION,
        PENDING_CONFIRMATION,
        APPROVED,
        REJECTED,
        COMPLETED,
        EXPIRED
    }

    @Override
    public String toString() {
        return "TraceInfo{" +
                "traceId='" + traceId + '\'' +
                ", userId='" + userId + '\'' +
                ", deviceId='" + deviceId + '\'' +
                ", deviceName='" + deviceName + '\'' +
                ", action='" + action + '\'' +
                ", command='" + command + '\'' +
                ", status='" + status + '\'' +
                ", threadId='" + threadId + '\'' +
                '}';
    }
}
