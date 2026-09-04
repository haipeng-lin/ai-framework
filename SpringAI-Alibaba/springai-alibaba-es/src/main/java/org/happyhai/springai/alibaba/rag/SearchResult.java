package org.happyhai.springai.alibaba.rag;

import java.util.Map;

/**
 * 检索结果 DTO
 */
public class SearchResult {

    private String id;
    private String content;
    private String fileName;
    private Double bm25Score;
    private Double knnScore;
    private Double rrfScore;
    private Integer bm25Rank;
    private Integer knnRank;
    private Integer rrfRank;
    private Map<String, Object> metadata;

    public SearchResult() {
    }

    public SearchResult(String id, String content, String fileName) {
        this.id = id;
        this.content = content;
        this.fileName = fileName;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public Double getBm25Score() { return bm25Score; }
    public void setBm25Score(Double bm25Score) { this.bm25Score = bm25Score; }

    public Double getKnnScore() { return knnScore; }
    public void setKnnScore(Double knnScore) { this.knnScore = knnScore; }

    public Double getRrfScore() { return rrfScore; }
    public void setRrfScore(Double rrfScore) { this.rrfScore = rrfScore; }

    public Integer getBm25Rank() { return bm25Rank; }
    public void setBm25Rank(Integer bm25Rank) { this.bm25Rank = bm25Rank; }

    public Integer getKnnRank() { return knnRank; }
    public void setKnnRank(Integer knnRank) { this.knnRank = knnRank; }

    public Integer getRrfRank() { return rrfRank; }
    public void setRrfRank(Integer rrfRank) { this.rrfRank = rrfRank; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
