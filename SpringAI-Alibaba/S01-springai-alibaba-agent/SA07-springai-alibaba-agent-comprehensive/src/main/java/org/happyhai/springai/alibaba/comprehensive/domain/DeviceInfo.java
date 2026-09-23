package org.happyhai.springai.alibaba.comprehensive.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

public class DeviceInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private String deviceId;
    private String productCode;
    private String deviceName;
    private boolean online;
    private String userId;
    private LocalDateTime createdAt;

    public DeviceInfo() {
    }

    public DeviceInfo(String deviceId, String productCode, String deviceName, boolean online, String userId) {
        this.deviceId = deviceId;
        this.productCode = productCode;
        this.deviceName = deviceName;
        this.online = online;
        this.userId = userId;
        this.createdAt = LocalDateTime.now();
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "DeviceInfo{" +
                "deviceId='" + deviceId + '\'' +
                ", productCode='" + productCode + '\'' +
                ", deviceName='" + deviceName + '\'' +
                ", online=" + online +
                ", userId='" + userId + '\'' +
                '}';
    }
}
