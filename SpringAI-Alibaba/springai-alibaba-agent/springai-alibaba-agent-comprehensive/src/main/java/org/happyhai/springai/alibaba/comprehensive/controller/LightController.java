package org.happyhai.springai.alibaba.comprehensive.controller;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.happyhai.springai.alibaba.comprehensive.domain.LightCommand;
import org.happyhai.springai.alibaba.comprehensive.domain.TraceInfo;
import org.happyhai.springai.alibaba.comprehensive.service.DeviceService;
import org.happyhai.springai.alibaba.comprehensive.service.TraceInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/aquarium-light")
public class LightController {

    private static final Logger logger = LoggerFactory.getLogger(LightController.class);

    private final TraceInfoService traceInfoService;
    private final DeviceService deviceService;
    private final ReactAgent reactAgent;
    private final ToolCallbackProvider toolCallbackProvider;
    private final LightSseService sseService;

    public LightController(TraceInfoService traceInfoService, DeviceService deviceService,
                           ReactAgent reactAgent, ToolCallbackProvider toolCallbackProvider,
                           LightSseService sseService) {
        this.traceInfoService = traceInfoService;
        this.deviceService = deviceService;
        this.reactAgent = reactAgent;
        this.toolCallbackProvider = toolCallbackProvider;
        this.sseService = sseService;
    }

    /**
     * 自然语言对话接口 - Agent 自主决定调用工具
     */
    @GetMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(
            @RequestParam String message,
            @RequestParam(required = false, defaultValue = "user001") String userId,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String selectedDeviceId) {

        logger.info("=== 对话请求 ===");
        logger.info("用户: {}, 消息: {}, traceId: {}, selectedDeviceId: {}", userId, message, traceId, selectedDeviceId);

        long totalStart = System.currentTimeMillis();

        String currentTraceId = traceId;
        String threadId;

        if (currentTraceId == null || currentTraceId.isEmpty()) {
            currentTraceId = traceInfoService.generateTraceId();
            threadId = "thread-" + currentTraceId;

            long redisSaveStart = System.currentTimeMillis();
            TraceInfo traceInfo = new TraceInfo(currentTraceId, userId, TraceInfo.Status.PENDING_DEVICE_SELECTION.name(), threadId);
            traceInfoService.saveTraceInfo(traceInfo);
            logger.info("[耗时] Redis保存TraceInfo: {}ms", System.currentTimeMillis() - redisSaveStart);
            logger.info("新对话，已生成 TraceId: {}", currentTraceId);
        } else {
            long redisGetStart = System.currentTimeMillis();
            Optional<TraceInfo> existingTrace = traceInfoService.getTraceInfo(currentTraceId);
            logger.info("[耗时] Redis查询TraceInfo: {}ms", System.currentTimeMillis() - redisGetStart);

            if (existingTrace.isPresent()) {
                threadId = existingTrace.get().getThreadId();
                logger.info("继续对话，traceId: {}", currentTraceId);
            } else {
                threadId = "thread-" + currentTraceId;
            }
        }

        try {
            String prompt;
            String action = detectAction(message);

            if (selectedDeviceId != null && !selectedDeviceId.isEmpty()) {
                long updateLcStart = System.currentTimeMillis();
                traceInfoService.updateLightControl(currentTraceId, selectedDeviceId,
                        selectedDeviceId, action, "unknown");
                logger.info("[耗时] Redis更新LightControl: {}ms", System.currentTimeMillis() - updateLcStart);

                prompt = String.format(
                        "用户ID: %s，用户请求: %s，用户操作: %s\n" +
                                "用户已选择设备，deviceIdentifier: %s\n\n" +
                                "第一步：再次调用 getOnlineDevicesByUniqueId(uniqueId=\"%s\") 确认该设备在线，获取完整设备信息。\n" +
                                "第二步：参考 SKILL.md，使用 publishDeviceCommands 发送控制命令，topic 格式为 device/{userId}{deviceIdentifier}/command，order 必须是十六进制命令（禁止 on/off）。",
                        userId, message, action, selectedDeviceId, userId
                );
            } else {
                prompt = String.format(
                        "用户ID: %s，用户请求: %s\n\n" +
                                "第一步：使用 getOnlineDevicesByUniqueId(uniqueId=\"%s\") 查询用户在线设备，将设备列表返回给用户确认。\n" +
                                "第二步：用户确认后再使用 publishDeviceCommands 发送控制命令，参考 SKILL.md 中 topic 和 order 的格式。",
                        userId, message, userId
                );
            }

            RunnableConfig config = RunnableConfig.builder()
                    .threadId(threadId)
                    .build();

            long agentStart = System.currentTimeMillis();
            Optional<NodeOutput> result = reactAgent.invokeAndGetOutput(prompt, config);
            long agentMs = System.currentTimeMillis() - agentStart;
            logger.info("[耗时] Agent执行: {}ms", agentMs);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("traceId", currentTraceId);
            response.put("threadId", threadId);
            response.put("userId", userId);

            if (result.isPresent()) {
                NodeOutput output = result.get();

                if (output instanceof InterruptionMetadata interruptionMetadata) {
                    logger.info("检测到中断 - 需要人工确认");

                    long toolArgsStart = System.currentTimeMillis();
                    List<Map<String, String>> feedbacks = new ArrayList<>();
                    String toolArgs = null;
                    for (InterruptionMetadata.ToolFeedback feedback : interruptionMetadata.toolFeedbacks()) {
                        feedbacks.add(Map.of(
                                "tool", feedback.getName(),
                                "args", feedback.getArguments(),
                                "description", feedback.getDescription()
                        ));
                        if ("publishDeviceCommands".equals(feedback.getName())) {
                            toolArgs = feedback.getArguments();
                        }
                    }
                    if (toolArgs != null) {
                        traceInfoService.updateToolArgs(currentTraceId, toolArgs);
                    }
                    logger.info("[耗时] 解析InterruptMetadata+Redis保存ToolArgs: {}ms", System.currentTimeMillis() - toolArgsStart);

                    response.put("status", "PENDING_CONFIRMATION");
                    response.put("interrupted", true);
                    response.put("toolFeedbacks", feedbacks);
                    response.put("message", "操作需要人工确认，请调用 /confirm 接口批准或拒绝");
                } else {
                    String outputStr = output.toString();

                    if (outputStr.contains("找到") && outputStr.contains("设备")) {
                        logger.info("返回设备列表供用户选择");
                        response.put("status", "NEED_DEVICE_SELECTION");
                        response.put("interrupted", false);
                        response.put("needDeviceSelection", true);
                        response.put("message", "请从上方设备列表中选择要控制的设备，回复设备对应的 deviceIdentifier");
                    } else {
                        logger.info("Agent 执行完成");
                        response.put("status", "COMPLETED");
                        response.put("interrupted", false);
                        response.put("message", outputStr);
                    }
                }
            } else {
                response.put("status", "PENDING_CONFIRMATION");
                response.put("interrupted", true);
            }

            long totalMs = System.currentTimeMillis() - totalStart;
            logger.info("[耗时] 对话请求总耗时: {}ms", totalMs);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("对话处理失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 确认灯光控制操作。
     */
    @PostMapping("/confirm")
    public ResponseEntity<Map<String, Object>> confirmLight(
            @RequestParam String traceId,
            @RequestParam boolean approved,
            @RequestParam(required = false) String feedback) {

        logger.info("=== 确认请求 ===");
        logger.info("TraceId: {}, 批准: {}", traceId, approved);
        long totalStart = System.currentTimeMillis();

        Optional<TraceInfo> optionalTraceInfo = traceInfoService.getTraceInfo(traceId);
        if (optionalTraceInfo.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "TraceId 不存在或已过期"
            ));
        }

        TraceInfo traceInfo = optionalTraceInfo.get();
        String threadId = traceInfo.getThreadId();

        Map<String, Object> response = new HashMap<>();
        response.put("traceId", traceId);
        String toolArgs = traceInfo.getToolArgs();
        String toolResult = null;

        try {
            if (approved) {
                traceInfoService.updateStatus(traceId, TraceInfo.Status.APPROVED);

                response.put("success", true);
                response.put("status", "APPROVED");
                response.put("message", "已批准，等待 Agent 执行设备控制命令");

                try {
                    long mcpStart = System.currentTimeMillis();
                    for (ToolCallback tc : toolCallbackProvider.getToolCallbacks()) {
                        if ("publishDeviceCommands".equals(tc.getToolDefinition().name())) {
                            logger.info("找到 MCP 工具，直接调用");
                            toolResult = tc.call(toolArgs);
                            break;
                        }
                    }
                    logger.info("[耗时] MCP工具publishDeviceCommands调用: {}ms, 结果: {}", System.currentTimeMillis() - mcpStart, toolResult);

                    boolean wokeSse = sseService.triggerConfirmation(traceId, "开灯指令已成功发送！");
                    if (!wokeSse) {
                        response.put("toolResult", toolResult);
                    }
                } catch (Exception e) {
                    logger.error("恢复 Agent 执行失败", e);
                    sseService.triggerConfirmation(traceId, "执行失败：" + e.getMessage());
                }

            } else {
                traceInfoService.updateStatus(traceId, TraceInfo.Status.REJECTED);
                response.put("success", true);
                response.put("status", "REJECTED");
                response.put("message", "操作已拒绝");
                if (feedback != null && !feedback.isEmpty()) {
                    response.put("feedback", feedback);
                }
                sseService.triggerConfirmation(traceId, "操作已拒绝");
            }

            long totalMs = System.currentTimeMillis() - totalStart;
            logger.info("[耗时] 确认请求总耗时: {}ms", totalMs);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("确认处理失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/devices")
    public ResponseEntity<Map<String, Object>> getDevices(
            @RequestParam(required = false, defaultValue = "user001") String userId) {
        long start = System.currentTimeMillis();
        String threadId = "thread-devices-" + UUID.randomUUID().toString().replace("-", "");
        String prompt = String.format(
                "用户ID: %s\n\n请使用 getOnlineDevicesByUniqueId(uniqueId=\"%s\") 查询用户在线设备，直接返回设备列表。",
                userId, userId
        );
        try {
            RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
            long agentStart = System.currentTimeMillis();
            Optional<NodeOutput> result = reactAgent.invokeAndGetOutput(prompt, config);
            logger.info("[耗时] Agent查询设备: {}ms", System.currentTimeMillis() - agentStart);

            String message = result.map(Object::toString).orElse("未返回结果");
            logger.info("[耗时] /devices请求总耗时: {}ms", System.currentTimeMillis() - start);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "userId", userId,
                    "message", message
            ));
        } catch (Exception e) {
            logger.error("查询设备失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/status/{traceId}")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable String traceId) {
        long start = System.currentTimeMillis();
        Optional<TraceInfo> optionalTraceInfo = traceInfoService.getTraceInfo(traceId);
        if (optionalTraceInfo.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "TraceId 不存在或已过期"
            ));
        }

        TraceInfo traceInfo = optionalTraceInfo.get();
        logger.info("[耗时] /status查询: {}ms", System.currentTimeMillis() - start);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "traceId", traceId,
                "status", traceInfo.getStatus(),
                "deviceId", traceInfo.getDeviceId(),
                "deviceName", traceInfo.getDeviceName(),
                "action", traceInfo.getAction(),
                "threadId", traceInfo.getThreadId(),
                "createdAt", traceInfo.getCreatedAt(),
                "updatedAt", traceInfo.getUpdatedAt()
        ));
    }

    @GetMapping("/commands")
    public ResponseEntity<Map<String, Object>> getCommands() {
        Map<String, LightCommand> commands = deviceService.getAllLightCommands();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "commands", commands.values()
        ));
    }

    private String resolveCommand(String productCode, String action) {
        if (productCode == null || "unknown".equals(productCode)) {
            return "unknown";
        }
        Optional<LightCommand> lightCommand = deviceService.getLightCommand(productCode);
        if (lightCommand.isEmpty()) {
            return "C90102A2010A0602001D";
        }
        return "开".equals(action) || "on".equalsIgnoreCase(action)
                ? lightCommand.get().getPowerOnCommand()
                : lightCommand.get().getPowerOffCommand();
    }

    private String detectAction(String message) {
        String lowerMsg = message.toLowerCase();
        boolean hasOn = lowerMsg.matches(".*(开|on|open|启动).*");
        boolean hasOff = lowerMsg.matches(".*(关|off|shutdown|close).*");
        if (hasOn && !hasOff) return "open";
        if (hasOff && !hasOn) return "close";
        if (hasOn && hasOff) return "open";
        return "close";
    }
}
