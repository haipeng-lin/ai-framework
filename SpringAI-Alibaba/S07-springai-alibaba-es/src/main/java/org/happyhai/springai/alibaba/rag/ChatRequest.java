package org.happyhai.springai.alibaba.rag;

/**
 * 问答请求 DTO
 */
public class ChatRequest {

    private String question;
    private Integer topK = 5;
    private Double bm25Weight = 0.4;
    private Double knnWeight = 0.6;
    private Double threshold = 0.0;

    public ChatRequest() {
    }

    public ChatRequest(String question) {
        this.question = question;
    }

    // Getters and Setters
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public Integer getTopK() { return topK; }
    public void setTopK(Integer topK) { this.topK = topK; }

    public Double getBm25Weight() { return bm25Weight; }
    public void setBm25Weight(Double bm25Weight) { this.bm25Weight = bm25Weight; }

    public Double getKnnWeight() { return knnWeight; }
    public void setKnnWeight(Double knnWeight) { this.knnWeight = knnWeight; }

    public Double getThreshold() { return threshold; }
    public void setThreshold(Double threshold) { this.threshold = threshold; }
}
