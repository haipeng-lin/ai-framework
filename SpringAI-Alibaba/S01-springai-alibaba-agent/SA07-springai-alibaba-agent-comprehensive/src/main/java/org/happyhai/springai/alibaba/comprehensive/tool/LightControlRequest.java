package org.happyhai.springai.alibaba.comprehensive.tool;

public class LightControlRequest {

    private String deviceId;
    private String command;

    public LightControlRequest() {
    }

    public LightControlRequest(String deviceId, String command) {
        this.deviceId = deviceId;
        this.command = command;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }
}
