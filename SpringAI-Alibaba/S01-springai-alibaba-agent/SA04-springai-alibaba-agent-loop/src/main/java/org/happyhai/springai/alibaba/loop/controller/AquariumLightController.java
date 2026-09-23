package org.happyhai.springai.alibaba.loop.controller;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.happyhai.springai.alibaba.loop.domain.TraceInfo;
import org.happyhai.springai.alibaba.loop.service.TraceInfoService;
import org.happyhai.springai.alibaba.loop.tool.AquariumLightTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/aquarium-light")
public class AquariumLightController {

    private static final Logger logger = LoggerFactory.getLogger(AquariumLightController.class);

    private final TraceInfoService traceInfoService;
    private final ReactAgent reactAgent;

    public AquariumLightController(TraceInfoService traceInfoService, ReactAgent reactAgent) {
        this.traceInfoService = traceInfoService;
        this.reactAgent = reactAgent;
    }

    /**
     * 自然语言对话接口 - 发送自然语言给 Agent，Agent 自主决定是否调用调光工具
     */
    @GetMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(
            @RequestParam String message,
            @RequestParam(required = false) String userId) {

        logger.info("=== 自然语言对话请求 ===");
        logger.info("用户消息: {}, 用户ID: {}", message, userId);

        String traceId = traceInfoService.generateTraceId();
        String threadId = "thread-" + traceId;

        TraceInfo traceInfo = new TraceInfo(
                traceId,
                null,
                null,
                null,
                TraceInfo.Status.PENDING.name(),
                threadId
        );
        traceInfoService.saveTraceInfo(traceInfo);

        logger.info("已生成 TraceId: {}, ThreadId: {}", traceId, threadId);

        try {
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(threadId)
                    .build();

            logger.info("开始调用 Agent - message: {}", message);

            Optional<NodeOutput> result = reactAgent.invokeAndGetOutput(message, config);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("traceId", traceId);
            response.put("threadId", threadId);

            if (result.isPresent()) {
                NodeOutput output = result.get();
                if (output instanceof InterruptionMetadata interruptionMetadata) {
                    logger.info("检测到人工介入中断 - nodeId: {}", interruptionMetadata.node());
                    
                    response.put("status", "PENDING_CONFIRMATION");
                    response.put("interrupted", true);
                    
                    for (InterruptionMetadata.ToolFeedback feedback : interruptionMetadata.toolFeedbacks()) {
                        logger.info("工具反馈 - name: {}, args: {}, description: {}",
                                feedback.getName(), feedback.getArguments(), feedback.getDescription());
                        
                        if ("aquarium_light_control".equals(feedback.getName())) {
                            response.put("pendingTool", "aquarium_light_control");
                            response.put("toolDescription", feedback.getDescription());
                        }
                    }
                    
                    response.put("message", "Agent 决定执行调光操作，需要人工确认");
                } else {
                    logger.info("Agent 执行完成");
                    response.put("status", "COMPLETED");
                    response.put("message", output.toString());
                    response.put("interrupted", false);
                }
            } else {
                response.put("status", "PENDING_CONFIRMATION");
                response.put("interrupted", true);
            }

            logger.info("返回响应: {}", response);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("对话请求处理失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("traceId", traceId);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 确认调光接口 - 批准或拒绝 Agent 提出的调光操作
     */
    @PostMapping("/confirm")
    public ResponseEntity<Map<String, Object>> confirmLight(
            @RequestParam String traceId,
            @RequestParam boolean approved,
            @RequestParam(required = false) String feedback) {

        logger.info("=== 确认调光请求 ===");
        logger.info("TraceId: {}, 批准: {}, 反馈: {}", traceId, approved, feedback);

        Optional<TraceInfo> optionalTraceInfo = traceInfoService.getTraceInfo(traceId);
        if (optionalTraceInfo.isEmpty()) {
            logger.warn("未找到 TraceInfo - traceId: {}", traceId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "TraceId 不存在或已过期");
            return ResponseEntity.badRequest().body(response);
        }

        TraceInfo traceInfo = optionalTraceInfo.get();
        String threadId = traceInfo.getThreadId();

        try {
            if (approved) {
                traceInfoService.updateTraceStatus(traceId, TraceInfo.Status.APPROVED);
                
                if (traceInfo.getLightName() != null) {
                    AquariumLightTool.executeLightControl(
                            traceInfo.getLightName(),
                            traceInfo.getPreset(),
                            traceInfo.getBrightness()
                    );
                }

                logger.info("调光已批准执行 - traceId: {}", traceId);

                try {
                    InterruptionMetadata.Builder feedbackBuilder = InterruptionMetadata.builder()
                            .nodeId(threadId)
                            .state(null);

                    InterruptionMetadata.ToolFeedback approvedFeedback = InterruptionMetadata.ToolFeedback.builder()
                            .name("aquarium_light_control")
                            .result(InterruptionMetadata.ToolFeedback.FeedbackResult.APPROVED)
                            .description("用户批准了水族灯调光操作")
                            .build();
                    feedbackBuilder.addToolFeedback(approvedFeedback);

                    InterruptionMetadata approvalMetadata = feedbackBuilder.build();

                    RunnableConfig resumeConfig = RunnableConfig.builder()
                            .threadId(threadId)
                            .addMetadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY, approvalMetadata)
                            .build();

                    Optional<NodeOutput> finalResult = reactAgent.invokeAndGetOutput("", resumeConfig);
                    logger.info("Agent 已恢复执行");

                } catch (Exception e) {
                    logger.warn("恢复 Agent 执行时出现异常（可能是非中断场景）: {}", e.getMessage());
                }

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("traceId", traceId);
                response.put("status", "APPROVED");
                response.put("message", "调光操作已批准执行");
                if (traceInfo.getLightName() != null) {
                    response.put("message", String.format("调光操作已批准，%s 已设置为 %s 模式，亮度 %d%%",
                            traceInfo.getLightName(), traceInfo.getPreset(), traceInfo.getBrightness()));
                }
                return ResponseEntity.ok(response);

            } else {
                traceInfoService.updateTraceStatus(traceId, TraceInfo.Status.REJECTED);
                
                logger.info("调光已拒绝 - traceId: {}", traceId);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("traceId", traceId);
                response.put("status", "REJECTED");
                response.put("message", "调光操作已拒绝");
                if (feedback != null && !feedback.isEmpty()) {
                    response.put("feedback", feedback);
                }
                return ResponseEntity.ok(response);
            }

        } catch (Exception e) {
            logger.error("确认调光处理失败 - traceId: {}", traceId, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("traceId", traceId);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/status/{traceId}")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable String traceId) {
        logger.info("查询调光状态 - traceId: {}", traceId);
        
        Optional<TraceInfo> optionalTraceInfo = traceInfoService.getTraceInfo(traceId);
        if (optionalTraceInfo.isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "TraceId 不存在或已过期");
            return ResponseEntity.badRequest().body(response);
        }

        TraceInfo traceInfo = optionalTraceInfo.get();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("traceId", traceId);
        response.put("status", traceInfo.getStatus());
        response.put("lightName", traceInfo.getLightName());
        response.put("preset", traceInfo.getPreset());
        response.put("brightness", traceInfo.getBrightness());
        response.put("threadId", traceInfo.getThreadId());
        response.put("createdAt", traceInfo.getCreatedAt());
        response.put("updatedAt", traceInfo.getUpdatedAt());

        return ResponseEntity.ok(response);
    }
}
