package org.happyhai.springai.alibaba.comprehensive.controller;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.happyhai.springai.alibaba.comprehensive.domain.DeviceInfo;
import org.happyhai.springai.alibaba.comprehensive.domain.LightCommand;
import org.happyhai.springai.alibaba.comprehensive.domain.TraceInfo;
import org.happyhai.springai.alibaba.comprehensive.service.DeviceService;
import org.happyhai.springai.alibaba.comprehensive.service.TraceInfoService;
import org.happyhai.springai.alibaba.comprehensive.tool.LightControlTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public LightController(TraceInfoService traceInfoService, DeviceService deviceService, ReactAgent reactAgent) {
        this.traceInfoService = traceInfoService;
        this.deviceService = deviceService;
        this.reactAgent = reactAgent;
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

        String currentTraceId = traceId;
        String threadId;

        if (currentTraceId == null || currentTraceId.isEmpty()) {
            currentTraceId = traceInfoService.generateTraceId();
            threadId = "thread-" + currentTraceId;

            TraceInfo traceInfo = new TraceInfo(currentTraceId, userId, TraceInfo.Status.PENDING_DEVICE_SELECTION.name(), threadId);
            traceInfoService.saveTraceInfo(traceInfo);
            logger.info("新对话，已生成 TraceId: {}", currentTraceId);
        } else {
            Optional<TraceInfo> existingTrace = traceInfoService.getTraceInfo(currentTraceId);
            if (existingTrace.isPresent()) {
                threadId = existingTrace.get().getThreadId();
                logger.info("继续对话，traceId: {}", currentTraceId);
            } else {
                threadId = "thread-" + currentTraceId;
            }
        }

        try {
            String prompt;
            
            if (selectedDeviceId != null && !selectedDeviceId.isEmpty()) {
                // 用户选择了设备，继续执行灯光控制
                Optional<DeviceInfo> deviceOpt = deviceService.getDeviceById(selectedDeviceId);
                if (deviceOpt.isEmpty()) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "success", false,
                            "error", "设备不存在: " + selectedDeviceId
                    ));
                }

                DeviceInfo device = deviceOpt.get();
                String action = detectAction(message);
                
                // 更新 traceInfo
                traceInfoService.updateLightControl(currentTraceId, device.getDeviceId(), 
                        device.getDeviceName(), action, device.getProductCode());

                prompt = String.format(
                        "当前用户ID: %s\n\n" +
                        "用户已选择设备:\n" +
                        "- 设备ID: %s\n" +
                        "- 设备名称: %s\n" +
                        "- 产品码: %s\n" +
                        "- 用户操作: %s\n\n" +
                        "请使用 light_control 工具执行灯光控制，deviceId 参数使用: %s，command 参数使用: %s",
                        userId, device.getDeviceId(), device.getDeviceName(), device.getProductCode(), action,
                        device.getDeviceId(), action
                );
            } else {
                // 首次请求，先查询设备
                prompt = String.format(
                        "当前用户ID: %s\n\n用户请求: %s\n\n请使用 device_query 工具查询该用户的在线设备，userId 参数必须使用: %s",
                        userId, message, userId
                );
            }

            RunnableConfig config = RunnableConfig.builder()
                    .threadId(threadId)
                    .build();

            Optional<NodeOutput> result = reactAgent.invokeAndGetOutput(prompt, config);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("traceId", currentTraceId);
            response.put("threadId", threadId);
            response.put("userId", userId);

            if (result.isPresent()) {
                NodeOutput output = result.get();
                
                if (output instanceof InterruptionMetadata interruptionMetadata) {
                    logger.info("检测到中断 - 需要人工确认");
                    response.put("status", "PENDING_CONFIRMATION");
                    response.put("interrupted", true);

                    List<Map<String, String>> feedbacks = new ArrayList<>();
                    for (InterruptionMetadata.ToolFeedback feedback : interruptionMetadata.toolFeedbacks()) {
                        feedbacks.add(Map.of(
                                "tool", feedback.getName(),
                                "args", feedback.getArguments(),
                                "description", feedback.getDescription()
                        ));
                    }
                    response.put("toolFeedbacks", feedbacks);
                    response.put("message", "操作需要人工确认，请调用 /confirm 接口批准或拒绝");
                } else {
                    String outputStr = output.toString();
                    
                    // 检查是否返回了设备列表（需要用户选择）
                    if (outputStr.contains("找到") && outputStr.contains("设备")) {
                        logger.info("返回设备列表供用户选择");
                        response.put("status", "NEED_DEVICE_SELECTION");
                        response.put("interrupted", false);
                        response.put("needDeviceSelection", true);
                        
                        // 解析设备列表
                        List<DeviceInfo> devices = deviceService.getOnlineDevicesByUserId(userId);
                        response.put("devices", devices);
                        response.put("message", "请选择要控制的设备");
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
     * 确认灯光控制操作
     */
    @PostMapping("/confirm")
    public ResponseEntity<Map<String, Object>> confirmLight(
            @RequestParam String traceId,
            @RequestParam boolean approved,
            @RequestParam(required = false) String feedback) {

        logger.info("=== 确认请求 ===");
        logger.info("TraceId: {}, 批准: {}", traceId, approved);

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

        try {
            if (approved) {
                String deviceId = traceInfo.getDeviceId();
                String action = traceInfo.getAction();
                String productCode = traceInfo.getCommand();

                String command = resolveCommand(productCode, action);
                LightControlTool.executeMqttCommand(deviceId, command);

                traceInfoService.updateStatus(traceId, TraceInfo.Status.APPROVED);

                response.put("success", true);
                response.put("status", "APPROVED");
                response.put("message", String.format("已执行 %s 操作，设备: %s", action, traceInfo.getDeviceName()));

                try {
                    InterruptionMetadata.Builder feedbackBuilder = InterruptionMetadata.builder()
                            .nodeId(threadId)
                            .state(null);

                    InterruptionMetadata.ToolFeedback approvedFeedback = InterruptionMetadata.ToolFeedback.builder()
                            .name("light_control")
                            .result(InterruptionMetadata.ToolFeedback.FeedbackResult.APPROVED)
                            .description("用户批准了灯光控制操作")
                            .build();
                    feedbackBuilder.addToolFeedback(approvedFeedback);

                    InterruptionMetadata approvalMetadata = feedbackBuilder.build();

                    RunnableConfig resumeConfig = RunnableConfig.builder()
                            .threadId(threadId)
                            .addMetadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY, approvalMetadata)
                            .build();

                    reactAgent.invokeAndGetOutput("", resumeConfig);
                    logger.info("Agent 已恢复执行");
                } catch (Exception e) {
                    logger.warn("恢复 Agent 执行时出现异常: {}", e.getMessage());
                }

            } else {
                traceInfoService.updateStatus(traceId, TraceInfo.Status.REJECTED);
                response.put("success", true);
                response.put("status", "REJECTED");
                response.put("message", "操作已拒绝");
                if (feedback != null && !feedback.isEmpty()) {
                    response.put("feedback", feedback);
                }
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("确认处理失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 查询设备列表
     */
    @GetMapping("/devices")
    public ResponseEntity<Map<String, Object>> getDevices(
            @RequestParam(required = false, defaultValue = "user001") String userId) {

        List<DeviceInfo> devices = deviceService.getOnlineDevicesByUserId(userId);
        List<DeviceInfo> allDevices = deviceService.getDevicesByUserId(userId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "onlineDevices", devices,
                "allDevices", allDevices
        ));
    }

    /**
     * 查询状态
     */
    @GetMapping("/status/{traceId}")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable String traceId) {
        Optional<TraceInfo> optionalTraceInfo = traceInfoService.getTraceInfo(traceId);
        if (optionalTraceInfo.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "TraceId 不存在或已过期"
            ));
        }

        TraceInfo traceInfo = optionalTraceInfo.get();
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

    /**
     * 获取支持的灯光命令码
     */
    @GetMapping("/commands")
    public ResponseEntity<Map<String, Object>> getCommands() {
        Map<String, LightCommand> commands = deviceService.getAllLightCommands();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "commands", commands.values()
        ));
    }

    private String resolveCommand(String productCode, String action) {
        Optional<LightCommand> lightCommand = deviceService.getLightCommand(productCode);
        if (lightCommand.isEmpty()) {
            logger.warn("未找到产品码命令: {}, 使用默认命令", productCode);
            return "C90102A2010A0602001D";
        }

        if ("开".equals(action) || "on".equalsIgnoreCase(action)) {
            return lightCommand.get().getPowerOnCommand();
        } else {
            return lightCommand.get().getPowerOffCommand();
        }
    }

    private String detectAction(String message) {
        String lowerMsg = message.toLowerCase();
        if (lowerMsg.contains("关") || lowerMsg.contains("off") || lowerMsg.contains("shutdown") || lowerMsg.contains("close")) {
            return "关";
        } else if (lowerMsg.contains("开") || lowerMsg.contains("on") || lowerMsg.contains("open") || lowerMsg.contains("启动")) {
            return "开";
        }
        return "关";
    }
}
