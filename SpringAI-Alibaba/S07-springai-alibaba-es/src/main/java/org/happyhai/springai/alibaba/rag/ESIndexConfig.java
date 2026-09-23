package org.happyhai.springai.alibaba.rag;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.StringReader;

/**
 * Elasticsearch 索引配置类
 * 配置 IK 分词器和混合检索字段映射
 */
@Component
public class ESIndexConfig {

    private static final Logger logger = LoggerFactory.getLogger(ESIndexConfig.class);

    /**
     * text-embedding-v3 固定维度
     */
    private static final int EMBEDDING_DIMENSIONS = 1024;

    private final ElasticsearchClient elasticsearchClient;

    @Value("${spring.ai.vectorstore.elasticsearch.index-name:spring-ai-es-rag-index}")
    private String indexName;

    public ESIndexConfig(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    @PostConstruct
    public void initIndex() {
        logger.info("开始初始化索引: {}", indexName);
        try {
            boolean exists = elasticsearchClient.indices()
                    .exists(ExistsRequest.of(e -> e.index(indexName)))
                    .value();

            if (exists) {
                logger.info("索引 {} 已存在，跳过创建", indexName);
                return;
            }

            createIndex();
            logger.info("索引 {} 创建成功", indexName);
        } catch (Exception e) {
            logger.error("初始化索引失败: {}", e.getMessage(), e);
            throw new RuntimeException("索引初始化失败", e);
        }
    }

    private void createIndex() throws Exception {
        String indexSettings = """
            {
              "settings": {
                "number_of_shards": 1,
                "number_of_replicas": 0,
                "analysis": {
                  "analyzer": {
                    "ik_max_word": {
                      "type": "custom",
                      "tokenizer": "ik_max_word",
                      "filter": ["lowercase"]
                    },
                    "ik_smart": {
                      "type": "custom",
                      "tokenizer": "ik_smart",
                      "filter": ["lowercase"]
                    }
                  }
                }
              },
              "mappings": {
                "properties": {
                  "id": { "type": "keyword" },
                  "content": {
                    "type": "text",
                    "analyzer": "ik_max_word",
                    "search_analyzer": "ik_smart"
                  },
                  "contentKeyword": {
                    "type": "text",
                    "analyzer": "ik_max_word",
                    "search_analyzer": "ik_smart",
                    "fields": {
                      "keyword": { "type": "keyword" }
                    }
                  },
                  "tokens": {
                    "type": "text",
                    "analyzer": "ik_max_word",
                    "search_analyzer": "ik_smart"
                  },
                  "fileName": { "type": "keyword" },
                  "fileSize": { "type": "long" },
                  "author": { "type": "text" },
                  "uploadTime": { "type": "date" },
                  "metadata": { "type": "object", "enabled": true },
                  "embedding": {
                    "type": "dense_vector",
                    "dims": %d,
                    "index": true,
                    "similarity": "cosine"
                  }
                }
              }
            }
            """.formatted(EMBEDDING_DIMENSIONS);

        elasticsearchClient.indices().create(CreateIndexRequest.of(c -> c
                .index(indexName)
                .withJson(new StringReader(indexSettings))
        ));
    }
}
