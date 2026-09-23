package org.happyhai.springai.alibaba.light.agent.domain;

public class BrightnessRequest {
    private int brightness;

    public BrightnessRequest() {}

    public BrightnessRequest(int brightness) { this.brightness = brightness; }

    public int getBrightness() { return brightness; }
    public void setBrightness(int brightness) { this.brightness = brightness; }
}
