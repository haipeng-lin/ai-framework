package org.happyhai.springai.alibaba.light.agent.domain;

public class ColorTemperatureRequest {
    private int temperature;

    public ColorTemperatureRequest() {}

    public ColorTemperatureRequest(int temperature) { this.temperature = temperature; }

    public int getTemperature() { return temperature; }
    public void setTemperature(int temperature) { this.temperature = temperature; }
}
