package org.happyhai.springai.alibaba.controller;

import org.happyhai.springai.alibaba.rag.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 混合检索 RAG Controller
 */
@RestController
@RequestMapping("/hybrid-rag")
public class HybridRagController {

    private static final Logger logger = LoggerFactory.getLogger(HybridRagController.class);

    private final ESDocumentUploadService uploadService;
    private final ESHybridSearchService searchService;
    private final ESRagChatService chatService;

    public HybridRagController(
            ESDocumentUploadService uploadService,
            ESHybridSearchService searchService,
            ESRagChatService chatService) {
        this.uploadService = uploadService;
        this.searchService = searchService;
        this.chatService = chatService;
    }

    // ==================== 文档上传接口 ====================

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "chunkSize", defaultValue = "512") int chunkSize,
            @RequestParam(value = "overlap", defaultValue = "50") int overlap) {

        logger.info("接收到文件上传请求: {}, chunkSize: {}, overlap: {}",
                file.getOriginalFilename(), chunkSize, overlap);

        try {
            String result = uploadService.uploadDocument(file, chunkSize, overlap);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", result,
                    "fileName", file.getOriginalFilename(),
                    "fileSize", file.getSize()
            ));
        } catch (Exception e) {
            logger.error("上传文件失败: {}", file.getOriginalFilename(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "上传失败: " + e.getMessage()
            ));
        }
    }

    @PostMapping(value = "/upload/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadDocuments(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "chunkSize", defaultValue = "512") int chunkSize,
            @RequestParam(value = "overlap", defaultValue = "50") int overlap) {

        logger.info("接收到批量文件上传请求: {} 个文件", files.size());

        int successCount = 0;
        int failCount = 0;
        StringBuilder messages = new StringBuilder();

        for (MultipartFile file : files) {
            try {
                uploadService.uploadDocument(file, chunkSize, overlap);
                successCount++;
                messages.append(String.format("✓ %s\n", file.getOriginalFilename()));
            } catch (Exception e) {
                failCount++;
                messages.append(String.format("✗ %s: %s\n", file.getOriginalFilename(), e.getMessage()));
            }
        }

        return ResponseEntity.ok(Map.of(
                "success", failCount == 0,
                "successCount", successCount,
                "failCount", failCount,
                "messages", messages.toString()
        ));
    }

    // ==================== 检索接口 ====================

    /**
     * 混合检索（BM25 + KNN + RRF 融合）
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam("query") String query,
            @RequestParam(value = "topK", defaultValue = "5") int topK,
            @RequestParam(value = "bm25Weight", defaultValue = "0.4") double bm25Weight,
            @RequestParam(value = "knnWeight", defaultValue = "0.6") double knnWeight,
            @RequestParam(value = "threshold", defaultValue = "0.0") double threshold) {

        logger.info("接收到混合检索请求: query={}, topK={}, bm25Weight={}, knnWeight={}",
                query, topK, bm25Weight, knnWeight);

        try {
            List<SearchResult> results = searchService.hybridSearch(
                    query, topK, bm25Weight, knnWeight, threshold);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "query", query,
                    "total", results.size(),
                    "results", results
            ));
        } catch (Exception e) {
            logger.error("混合检索失败: {}", query, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "检索失败: " + e.getMessage()
            ));
        }
    }

    // ==================== 问答接口 ====================

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody ChatRequest request) {
        logger.info("接收到问答请求: {}", request.getQuestion());

        try {
            ChatResponse response = chatService.chat(request);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "question", response.getQuestion(),
                    "answer", response.getAnswer(),
                    "model", response.getModel(),
                    "sources", response.getSources()
            ));
        } catch (Exception e) {
            logger.error("问答失败: {}", request.getQuestion(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "问答失败: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/chat")
    public ResponseEntity<Map<String, Object>> chatGet(
            @RequestParam("question") String question,
            @RequestParam(value = "topK", defaultValue = "5") int topK,
            @RequestParam(value = "bm25Weight", defaultValue = "0.4") double bm25Weight,
            @RequestParam(value = "knnWeight", defaultValue = "0.6") double knnWeight) {

        ChatRequest request = new ChatRequest(question);
        request.setTopK(topK);
        request.setBm25Weight(bm25Weight);
        request.setKnnWeight(knnWeight);

        return chat(request);
    }

    // ==================== 管理接口 ====================

    @DeleteMapping("/document/{id}")
    public ResponseEntity<Map<String, Object>> deleteDocument(@PathVariable String id) {
        try {
            String result = uploadService.deleteDocument(id);
            return ResponseEntity.ok(Map.of("success", true, "message", result));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false, "message", e.getMessage()));
        }
    }

    @DeleteMapping("/document/file/{fileName}")
    public ResponseEntity<Map<String, Object>> deleteByFileName(@PathVariable String fileName) {
        try {
            String result = uploadService.deleteByFileName(fileName);
            return ResponseEntity.ok(Map.of("success", true, "message", result));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false, "message", e.getMessage()));
        }
    }

    @DeleteMapping("/clear")
    public ResponseEntity<Map<String, Object>> clearIndex() {
        try {
            String result = uploadService.clearIndex();
            return ResponseEntity.ok(Map.of("success", true, "message", result));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        try {
            Map<String, Object> stats = searchService.getIndexStats();
            return ResponseEntity.ok(Map.of("success", true, "data", stats));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false, "message", e.getMessage()));
        }
    }
}