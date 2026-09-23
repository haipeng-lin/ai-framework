package org.happyhai.springai.alibaba.light.agent.domain;

public class FaultSymptomRequest {
    private String symptom;

    public FaultSymptomRequest() {}

    public FaultSymptomRequest(String symptom) { this.symptom = symptom; }

    public String getSymptom() { return symptom; }
    public void setSymptom(String symptom) { this.symptom = symptom; }
}
