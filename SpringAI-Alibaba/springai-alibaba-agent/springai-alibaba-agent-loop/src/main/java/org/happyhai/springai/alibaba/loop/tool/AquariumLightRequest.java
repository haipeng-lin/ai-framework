package org.happyhai.springai.alibaba.loop.tool;

public class AquariumLightRequest {

    private String lightName;
    private String preset;
    private Integer brightness;

    public AquariumLightRequest() {
    }

    public AquariumLightRequest(String lightName, String preset, Integer brightness) {
        this.lightName = lightName;
        this.preset = preset;
        this.brightness = brightness;
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

    @Override
    public String toString() {
        return "AquariumLightRequest{" +
                "lightName='" + lightName + '\'' +
                ", preset='" + preset + '\'' +
                ", brightness=" + brightness +
                '}';
    }
}
