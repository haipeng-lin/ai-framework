package org.happyhai.springai.alibaba.rag;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ES 混合检索服务
 * 支持 BM25 + KNN + RRF 融合检索
 */
@Service
public class ESHybridSearchService {

    private static final Logger logger = LoggerFactory.getLogger(ESHybridSearchService.class);
    private static final int RRF_K = 60;

    private final ElasticsearchClient elasticsearchClient;
    private final EmbeddingModel embeddingModel;
    private final String indexName;

    public ESHybridSearchService(
            ElasticsearchClient elasticsearchClient,
            @Qualifier("dashScopeEmbeddingModel") EmbeddingModel embeddingModel,
            @Value("${spring.ai.vectorstore.elasticsearch.index-name:spring-ai-es-rag-index}") String indexName) {
        this.elasticsearchClient = elasticsearchClient;
        this.embeddingModel = embeddingModel;
        this.indexName = indexName;
    }

    /**
     * 混合检索（BM25 + KNN，应用层 RRF 融合）
     */
    public List<SearchResult> hybridSearch(String query,
                                            int topK,
                                            double bm25Weight,
                                            double knnWeight,
                                            double threshold) {
        try {
            float[] queryVector = generateEmbedding(query);
            List<Float> queryVectorList = new ArrayList<>();
            for (float v : queryVector) {
                queryVectorList.add(v);
            }

            // 1. BM25 检索
            SearchResponse<Map> bm25Response = elasticsearchClient.search(s -> s
                    .index(indexName)
                    .size(topK * 2)
                    .query(q -> q
                            .bool(b -> b
                                    .should(sh -> sh.match(m -> m
                                            .field("content")
                                            .query(query)
                                            .boost(2.0f)
                                    ))
                                    .should(sh -> sh.match(m -> m
                                            .field("tokens")
                                            .query(query)
                                            .boost(1.5f)
                                    ))
                                    .should(sh -> sh.match(m -> m
                                            .field("contentKeyword")
                                            .query(query)
                                            .boost(1.0f)
                                    ))
                            )
                    )
            , Map.class);

            // 2. KNN 向量检索
            SearchResponse<Map> knnResponse = elasticsearchClient.search(s -> s
                    .index(indexName)
                    .size(topK * 2)
                    .knn(k -> k
                            .field("embedding")
                            .queryVector(queryVectorList)
                            .k(topK * 2)
                            .numCandidates(topK * 4)
                    )
            , Map.class);

            // 收集 BM25 结果
            Map<String, SearchResult> docMap = new LinkedHashMap<>();
            Map<String, Integer> bm25Ranks = new LinkedHashMap<>();
            int bm25Idx = 0;

            for (Hit<Map> hit : bm25Response.hits().hits()) {
                String id = hit.id();
                SearchResult result = buildResult(id, hit.source());
                if (result == null) continue;

                bm25Ranks.put(id, ++bm25Idx);
                result.setBm25Score(hit.score());
                result.setBm25Rank(bm25Idx);
                docMap.put(id, result);
            }

            // 收集 KNN 结果
            Map<String, Integer> knnRanks = new LinkedHashMap<>();
            int knnIdx = 0;

            for (Hit<Map> hit : knnResponse.hits().hits()) {
                String id = hit.id();
                knnRanks.put(id, ++knnIdx);

                if (!docMap.containsKey(id)) {
                    SearchResult result = buildResult(id, hit.source());
                    if (result != null) {
                        result.setKnnScore(hit.score());
                        result.setKnnRank(knnIdx);
                        docMap.put(id, result);
                    }
                } else {
                    docMap.get(id).setKnnScore(hit.score());
                }
            }

            // 应用层 RRF 融合
            List<SearchResult> results = applyRrf(
                    new ArrayList<>(docMap.values()),
                    bm25Ranks, knnRanks,
                    bm25Weight, knnWeight);

            return results.stream()
                    .filter(r -> r.getRrfScore() >= threshold)
                    .limit(topK)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            logger.error("混合检索失败: {}", e.getMessage(), e);
            throw new RuntimeException("混合检索失败", e);
        }
    }

    /**
     * 应用层 RRF 融合
     */
    private List<SearchResult> applyRrf(List<SearchResult> docs,
                                          Map<String, Integer> bm25Ranks,
                                          Map<String, Integer> knnRanks,
                                          double bm25Weight, double knnWeight) {
        Map<String, Double> rrfScores = new HashMap<>();

        for (SearchResult doc : docs) {
            String id = doc.getId();
            int bm25Rank = bm25Ranks.getOrDefault(id, Integer.MAX_VALUE);
            int knnRank = knnRanks.getOrDefault(id, Integer.MAX_VALUE);

            // 设置 BM25 和 KNN 排名
            doc.setBm25Rank(bm25Rank < Integer.MAX_VALUE ? bm25Rank : null);
            doc.setKnnRank(knnRank < Integer.MAX_VALUE ? knnRank : null);

            double totalWeight = bm25Weight + knnWeight;
            double normBm25 = bm25Weight / totalWeight;
            double normKnn = knnWeight / totalWeight;

            double score = 0.0;
            if (bm25Rank < Integer.MAX_VALUE && knnRank < Integer.MAX_VALUE) {
                score = normBm25 / (RRF_K + bm25Rank) + normKnn / (RRF_K + knnRank);
            } else if (bm25Rank < Integer.MAX_VALUE) {
                score = normBm25 / (RRF_K + bm25Rank);
            } else if (knnRank < Integer.MAX_VALUE) {
                score = normKnn / (RRF_K + knnRank);
            }

            rrfScores.put(id, score);
        }

        List<SearchResult> sorted = docs.stream()
                .sorted((a, b) -> Double.compare(
                        rrfScores.getOrDefault(b.getId(), 0.0),
                        rrfScores.getOrDefault(a.getId(), 0.0)))
                .collect(Collectors.toList());

        for (int i = 0; i < sorted.size(); i++) {
            SearchResult doc = sorted.get(i);
            doc.setRrfScore(rrfScores.get(doc.getId()));
            doc.setRrfRank(i + 1);
        }

        return sorted;
    }

    private SearchResult buildResult(String docId, Map<String, Object> source) {
        if (source == null) return null;
        SearchResult result = new SearchResult();
        result.setId(docId);
        result.setContent((String) source.get("content"));
        result.setFileName((String) source.get("fileName"));
        result.setMetadata((Map<String, Object>) source.get("metadata"));
        return result;
    }

    private float[] generateEmbedding(String text) {
        try {
            return embeddingModel.embed(text);
        } catch (Exception e) {
            logger.error("生成 embedding 失败: {}", e.getMessage(), e);
            return new float[1024];
        }
    }

    public Map<String, Object> getIndexStats() {
        try {
            var response = elasticsearchClient.count(c -> c.index(indexName));
            Map<String, Object> stats = new HashMap<>();
            stats.put("indexName", indexName);
            stats.put("documentCount", response.count());
            return stats;
        } catch (Exception e) {
            logger.error("获取索引统计失败: {}", e.getMessage(), e);
            return Map.of("error", e.getMessage());
        }
    }
}