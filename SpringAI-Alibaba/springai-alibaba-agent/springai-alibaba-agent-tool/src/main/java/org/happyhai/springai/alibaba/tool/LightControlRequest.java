package org.happyhai.springai.alibaba.tool;

public class LightControlRequest {
    private String lightName;
    private String preset;

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
}
