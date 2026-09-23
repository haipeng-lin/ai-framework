package org.happyhai.springai.alibaba.rag;

import java.util.List;

/**
 * 问答响应 DTO
 */
public class ChatResponse {

    private String answer;
    private List<SearchResult> sources;
    private String question;
    private String model;

    public ChatResponse() {
    }

    public ChatResponse(String answer, List<SearchResult> sources, String question) {
        this.answer = answer;
        this.sources = sources;
        this.question = question;
    }

    // Getters and Setters
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public List<SearchResult> getSources() { return sources; }
    public void setSources(List<SearchResult> sources) { this.sources = sources; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
}
