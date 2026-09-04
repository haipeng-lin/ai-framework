package org.happyhai.springai.alibaba.tool;

public class RecommendActivityRequest {

    private String city;

    public RecommendActivityRequest() {
    }

    public RecommendActivityRequest(String city) {
        this.city = city;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }
}
