package org.happyhai.springai.alibaba.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.Builder;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagService {

    private static final Logger logger = LoggerFactory.getLogger(RagService.class);

    private final VectorStore vectorStore;
    private final ChatClient dashScopeChatClient;
    private final Builder chatClientBuilder;

    public RagService(
            VectorStore vectorStore,
            @Qualifier("dashScopeChatClient") ChatClient dashScopeChatClient,
            @Qualifier("dashScopeChatClientBuilder") Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        this.dashScopeChatClient = dashScopeChatClient;
        this.chatClientBuilder = chatClientBuilder;
    }

    /**
     * 添加文档到向量数据库
     */
    public void addDocuments(List<String> documents) {
        List<Document> docs = documents.stream()
                .map(Document::new)
                .toList();
        vectorStore.add(docs);
    }

    /**
     * 根据 ID 删除文档
     */
    public void deleteDocuments(List<String> documentIds) {
        vectorStore.delete(documentIds);
    }

    /**
     * 相似度搜索
     */
    public List<Document> similaritySearch(String query, int topK) {
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .build()
        );
    }

    /**
     * RAG 问答 - 使用 RetrievalAugmentationAdvisor 高级版（带查询重写）
     */
    public String ragAdvanced(String question, int topK, Builder chatClientBuilder) {
        var retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor.builder()
                .queryTransformers(RewriteQueryTransformer.builder()
                        .chatClientBuilder(chatClientBuilder.build().mutate())
                        .build())
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .similarityThreshold(0.5)
                        .topK(topK)
                        .vectorStore(vectorStore)
                        .build())
                .build();

        return chatClientBuilder.build().prompt()
                .advisors(retrievalAugmentationAdvisor)
                .user(question)
                .call()
                .content();
    }


    /**
     * 测试查询重写 - 只返回重写后的查询，不执行完整 RAG
     */
    public String testQueryRewrite(String question, Builder chatClientBuilder) {
        RewriteQueryTransformer transformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(chatClientBuilder.build().mutate())
                .build();

        Query originalQuery = new Query(question);
        Query transformedQuery = transformer.transform(originalQuery);

        logger.info("原始查询: {}", question);
        logger.info("重写后查询: {}", transformedQuery.text());

        return """
                原始查询: %s
                重写后查询: %s
                """.formatted(question, transformedQuery);
    }

    /**
     * 手动实现 RAG（更灵活的控制）
     */
    public String ragManual(String question, int topK) {
        // 1. 检索相关文档
        List<Document> documents = similaritySearch(question, topK);

        // 2. 构建上下文
        StringBuilder context = new StringBuilder();
        for (Document doc : documents) {
            context.append(doc.getText()).append("\n\n");
        }

        // 3. 构建提示词
        String prompt = """
                你是一个问答助手。请根据以下上下文来回答用户的问题。
                如果上下文中没有相关信息，请如实告知用户。

                上下文：
                %s

                问题：%s

                请基于上下文回答问题：
                """.formatted(context.toString(), question);

        // 4. 调用模型
        return dashScopeChatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }
}
