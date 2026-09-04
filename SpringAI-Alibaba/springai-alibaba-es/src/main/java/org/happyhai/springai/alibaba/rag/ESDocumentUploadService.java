package org.happyhai.springai.alibaba.rag;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import org.happyhai.springai.alibaba.service.DocumentChunkingService;
import org.happyhai.springai.alibaba.service.TikaParserService;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 文档上传服务
 * 解析文档、使用 IK 分词器分词、存入 ES
 */
@Service
public class ESDocumentUploadService {

    private static final Logger logger = LoggerFactory.getLogger(ESDocumentUploadService.class);

    private final ElasticsearchClient elasticsearchClient;
    private final EmbeddingModel embeddingModel;
    private final String indexName
            ;
    private final TikaParserService tikaParserService;
    private final DocumentChunkingService chunkingService;

    public ESDocumentUploadService(
            ElasticsearchClient elasticsearchClient,
            @Qualifier("dashScopeEmbeddingModel") EmbeddingModel embeddingModel,
            @Value("${spring.ai.vectorstore.elasticsearch.index-name:spring-ai-es-rag-index}") String indexName,
            TikaParserService tikaParserService,
            DocumentChunkingService chunkingService) {
        this.elasticsearchClient = elasticsearchClient;
        this.embeddingModel = embeddingModel;
        this.indexName = indexName;
        this.tikaParserService = tikaParserService;
        this.chunkingService = chunkingService;
    }

    /**
     * 上传并解析单个文档
     */
    public String uploadDocument(MultipartFile file, int chunkSize, int overlap) {
        String tempDir = System.getProperty("java.io.tmpdir");
        String originalFilename = file.getOriginalFilename();
        File tempFile = new File(tempDir, UUID.randomUUID() + "_" + originalFilename);

        try {
            file.transferTo(tempFile);
            var parsed = tikaParserService.parseFileWithMetadata(tempFile);
            String content = parsed.content();

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("fileName", originalFilename);
            metadata.put("fileSize", file.getSize());
            metadata.put("contentType", file.getContentType());
            parsed.metadata().forEach(metadata::putIfAbsent);

            // IK 分词
            String tokens = analyzeWithIk(content);

            // 切片
            List<Document> chunks = chunkingService.chunkByFixedSize(content, chunkSize, overlap, metadata);

            if (chunks.isEmpty()) {
                return "文档内容为空，无法处理";
            }

            int successCount = 0;
            for (Document chunk : chunks) {
                String chunkId = UUID.randomUUID().toString();
                String chunkContent = chunk.getText();

                // 生成向量 - 使用单个字符串参数的方法
                float[] embedding = generateEmbedding(chunkContent);

                // 获取该 chunk 的分词
                String chunkTokens = analyzeWithIk(chunkContent);

                Map<String, Object> doc = new HashMap<>();
                doc.put("id", chunkId);
                doc.put("content", chunkContent);
                doc.put("contentKeyword", chunkContent);
                doc.put("tokens", chunkTokens);
                doc.put("fileName", originalFilename);
                doc.put("fileSize", file.getSize());
                doc.put("author", metadata.getOrDefault("author", ""));
                doc.put("uploadTime", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
                doc.put("metadata", chunk.getMetadata());
                doc.put("embedding", embedding);

                elasticsearchClient.index(IndexRequest.of(i -> i
                        .index(indexName)
                        .id(chunkId)
                        .document(doc)
                ));
                successCount++;
            }

            logger.info("成功上传文档: {}, 切片数: {}", originalFilename, successCount);
            return String.format("成功上传文档: %s, 切片数: %d", originalFilename, successCount);

        } catch (Exception e) {
            logger.error("上传文档失败: {}", originalFilename, e);
            throw new RuntimeException("上传文档失败: " + originalFilename, e);
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    public String uploadDocument(MultipartFile file) {
        return uploadDocument(file, 512, 50);
    }

    private String analyzeWithIk(String text) {
        try {
            var response = elasticsearchClient.indices().analyze(a -> a
                    .index(indexName)
                    .analyzer("ik_max_word")
                    .text(text)
            );

            StringBuilder tokens = new StringBuilder();
            response.tokens().forEach(token -> {
                if (tokens.length() > 0) {
                    tokens.append(" ");
                }
                tokens.append(token.token());
            });
            return tokens.toString();
        } catch (Exception e) {
            logger.warn("IK 分词失败，使用原始文本: {}", e.getMessage());
            return text;
        }
    }

    private float[] generateEmbedding(String text) {
        try {
            // 使用单个字符串参数的 embed 方法
            float[] embedding = embeddingModel.embed(text);
            return embedding;
        } catch (Exception e) {
            logger.error("生成 embedding 失败: {}", e.getMessage(), e);
            return new float[1024];
        }
    }

    public String deleteDocument(String documentId) {
        try {
            elasticsearchClient.delete(d -> d
                    .index(indexName)
                    .id(documentId)
            );
            return "删除成功: " + documentId;
        } catch (Exception e) {
            logger.error("删除文档失败: {}", documentId, e);
            throw new RuntimeException("删除文档失败: " + documentId, e);
        }
    }

    public String deleteByFileName(String fileName) {
        try {
            elasticsearchClient.deleteByQuery(d -> d
                    .index(indexName)
                    .query(q -> q.term(t -> t.field("fileName.keyword").value(fileName)))
            );
            return "删除文件成功: " + fileName;
        } catch (Exception e) {
            logger.error("删除文件失败: {}", fileName, e);
            throw new RuntimeException("删除文件失败: " + fileName, e);
        }
    }

    public String clearIndex() {
        try {
            elasticsearchClient.deleteByQuery(d -> d
                    .index(indexName)
                    .query(q -> q.matchAll(m -> m))
            );
            return "索引已清空";
        } catch (Exception e) {
            logger.error("清空索引失败", e);
            throw new RuntimeException("清空索引失败", e);
        }
    }
}
