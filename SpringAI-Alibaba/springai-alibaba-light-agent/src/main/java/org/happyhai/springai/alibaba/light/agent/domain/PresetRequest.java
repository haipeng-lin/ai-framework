package org.happyhai.springai.alibaba.light.agent.domain;

public class PresetRequest {
    private String mode;

    public PresetRequest() {}

    public PresetRequest(String mode) { this.mode = mode; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
}
