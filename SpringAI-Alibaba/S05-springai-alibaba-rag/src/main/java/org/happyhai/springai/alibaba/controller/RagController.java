package org.happyhai.springai.alibaba.controller;

import org.happyhai.springaialibaba.springaialibaba.service.RagService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/rag")
public class RagController {

    private final RagService ragService;
    private final ChatClient.Builder chatClientBuilder;

    public RagController(RagService ragService, @Qualifier("deepSeekChatClientBuilder") ChatClient.Builder chatClientBuilder) {
        this.ragService = ragService;
        this.chatClientBuilder = chatClientBuilder;
    }

    /**
     * 添加文档到向量数据库
     */
    @PostMapping("/documents")
    public String addDocuments(@RequestBody List<String> documents) {
        ragService.addDocuments(documents);
        return "成功添加 " + documents.size() + " 个文档";
    }

    /**
     * 删除文档
     */
    @DeleteMapping("/documents")
    public String deleteDocuments(@RequestBody List<String> documentIds) {
        ragService.deleteDocuments(documentIds);
        return "成功删除 " + documentIds.size() + " 个文档";
    }

    /**
     * 相似度搜索
     */
    @GetMapping("/search")
    public List<Document> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "3") int topK) {
        return ragService.similaritySearch(query, topK);
    }

    /**
     * RAG 问答
     */
    @GetMapping("/chat")
    public String ragChat(
            @RequestParam String question,
            @RequestParam(defaultValue = "3") int topK) {
        return ragService.ragAdvanced(question, topK, chatClientBuilder);
    }

    /**
     * 测试查询重写
     */
    @GetMapping("/test-query-rewrite")
    public String testQueryRewrite(@RequestParam String question) {
        return ragService.testQueryRewrite(question, chatClientBuilder);
    }
}
