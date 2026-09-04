package org.happyhai.springai.alibaba.comprehensive.tool;

public class DeviceQueryRequest {

    private String userId;
    private boolean onlineOnly;

    public DeviceQueryRequest() {
    }

    public DeviceQueryRequest(String userId, boolean onlineOnly) {
        this.userId = userId;
        this.onlineOnly = onlineOnly;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public boolean isOnlineOnly() {
        return onlineOnly;
    }

    public void setOnlineOnly(boolean onlineOnly) {
        this.onlineOnly = onlineOnly;
    }
}
