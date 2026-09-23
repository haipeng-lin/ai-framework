package org.happyhai.springai.alibaba.comprehensive.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.happyhai.springai.alibaba.comprehensive.domain.TraceInfo;
import org.happyhai.springai.alibaba.comprehensive.service.TraceInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/aquarium-light")
public class LightSseController {

    private static final Logger logger = LoggerFactory.getLogger(LightSseController.class);
    private static final ObjectMapper objectMapper = LightSseService.objectMapper();

    private final TraceInfoService traceInfoService;
    private final ReactAgent reactAgent;
    private final LightSseService sseService;

    public LightSseController(TraceInfoService traceInfoService,
                              ReactAgent reactAgent,
                              LightSseService sseService) {
        this.traceInfoService = traceInfoService;
        this.reactAgent = reactAgent;
        this.sseService = sseService;
    }

    @GetMapping(value = "/chat-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<String> chatStream(
            @RequestParam String message,
            @RequestParam(required = false, defaultValue = "user001") String userId,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String selectedDeviceId) {

        logger.info("=== SSE dialogue request ===");
        logger.info("user: {}, message: {}, traceId: {}, selectedDeviceId: {}", userId, message, traceId, selectedDeviceId);

        String threadId;

        final String currentTraceId;
        if (traceId == null || traceId.isEmpty()) {
            currentTraceId = traceInfoService.generateTraceId();
            threadId = "thread-" + currentTraceId;
            TraceInfo traceInfo = new TraceInfo(currentTraceId, userId,
                    TraceInfo.Status.PENDING_DEVICE_SELECTION.name(), threadId);
            traceInfoService.saveTraceInfo(traceInfo);
            logger.info("New dialogue, TraceId: {}", currentTraceId);
        } else {
            currentTraceId = traceId;
            Optional<TraceInfo> existingTrace = traceInfoService.getTraceInfo(currentTraceId);
            threadId = existingTrace.map(TraceInfo::getThreadId)
                    .orElse("thread-" + currentTraceId);
            logger.info("Continuing dialogue, traceId: {}", currentTraceId);
        }

        String action = detectAction(message);

        String prompt;
        if (selectedDeviceId != null && !selectedDeviceId.isEmpty()) {
            traceInfoService.updateLightControl(currentTraceId, selectedDeviceId,
                    selectedDeviceId, action, "unknown");
            prompt = String.format(
                    "userID: %s, user request: %s, user action: %s\n" +
                            "user has selected device, deviceIdentifier: %s\n\n" +
                            "Step 1: call getOnlineDevicesByUniqueId(uniqueId=\"%s\") to confirm the device is online.\n" +
                            "Step 2: use publishDeviceCommands to send the control command, topic format: device/{userId}{deviceIdentifier}/command, order must be hexadecimal (no on/off).",
                    userId, message, action, selectedDeviceId, userId
            );
        } else {
            prompt = String.format(
                    "userID: %s, user request: %s\n\n" +
                            "Step 1: use getOnlineDevicesByUniqueId(uniqueId=\"%s\") to query the user online devices and return the list for confirmation.\n" +
                            "Step 2: after user confirms, use publishDeviceCommands to send the control command.",
                    userId, message, userId
            );
        }

        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();

        Optional<NodeOutput> result;
        try {
            result = reactAgent.invokeAndGetOutput(prompt, config);
        } catch (Exception e) {
            logger.error("Agent execution failed", e);
            return Flux.just(buildSse("message", Map.of("type", "END", "message", "处理失败：" + e.getMessage())));
        }

        if (result.isEmpty()) {
            return Flux.just(buildSse("message", Map.of("type", "END", "message", "未返回有效结果，请稍后重试。")));
        }

        NodeOutput output = result.get();

        if (output instanceof InterruptionMetadata interruptionMetadata) {
            logger.info("Detected interruption - needs human confirmation");

            String toolArgs = null;
            List<Map<String, String>> feedbacks = new ArrayList<>();
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

            Map<String, String> actionDetail = parseActionDetail(toolArgs, action, selectedDeviceId);
            sseService.registerTrace(currentTraceId);

            return Flux.concat(
                    Flux.just(buildSse("message", Map.of("type", "START", "message", "正在处理您的请求..."))),
                    Flux.just(buildSse("action_confirmation", Map.of(
                            "title", "确认操作",
                            "prompt", "即将执行设备控制操作，是否继续？",
                            "action", actionDetail,
                            "feedbacks", feedbacks
                    ))),
                    Flux.defer(() -> Flux.just(buildSse("message", Map.of("type", "END", "message", "等待确认中...")))
                            .publishOn(Schedulers.boundedElastic())
                            .flatMap(ignored -> {
                                String raw = sseService.awaitConfirmation(currentTraceId, 10 * 60 * 1000L);
                                String endMsg = raw != null ? raw : "操作已超时或被拒绝";
                                return Flux.just(buildSse("message", Map.of("type", "END", "message", endMsg)));
                            }))
            );
        }

        String outputStr = output.toString();
        String text = extractAssistantText(outputStr);

        if (text != null && text.contains("device")) {
            logger.info("Returning device list for user to select");
            List<Map<String, String>> devices = parseDevicesFromState(outputStr);
            return Flux.just(
                    buildSse("message", Map.of("type", "START", "message", "正在处理您的请求...")),
                    buildSse("device_selection", Map.of(
                            "title", "请选择要控制的设备",
                            "prompt", "检测到您有多台在线设备，请选择需要控制的设备：",
                            "devices", devices,
                            "traceId", currentTraceId
                    ))
            );
        } else {
            return Flux.just(
                    buildSse("message", Map.of("type", "START", "message", "正在处理您的请求...")),
                    buildSse("message", Map.of("type", "END", "message", text != null ? text : outputStr))
            );
        }
    }

    private String buildSse(String eventName, Object data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            return "event: " + eventName + "\ndata: " + json + "\n\n";
        } catch (JsonProcessingException e) {
            logger.error("SSE serialization failed", e);
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private String extractAssistantText(String nodeOutputStr) {
        try {
            int stateStart = nodeOutputStr.indexOf("state=");
            if (stateStart < 0) return null;
            int jsonStart = nodeOutputStr.indexOf("{", stateStart);
            if (jsonStart < 0) return null;
            int jsonEnd = nodeOutputStr.lastIndexOf("}");
            if (jsonEnd <= jsonStart) return null;
            String stateJson = nodeOutputStr.substring(jsonStart, jsonEnd + 1);
            JsonNode root = objectMapper.readTree(stateJson);
            JsonNode messages = root.at("/OverAllState/data/messages");
            if (!messages.isArray()) return null;

            // 优先从 TOOL 消息的 responseData 中取设备信息（用于 device_selection）
            for (JsonNode msg : messages) {
                if ("TOOL".equals(msg.path("messageType").asText(null))) {
                    JsonNode responses = msg.path("responses");
                    if (!responses.isArray() || responses.isEmpty()) continue;
                    JsonNode firstResp = responses.get(0);
                    String respData = firstResp.path("responseData").asText(null);
                    if (respData != null && !respData.isBlank() && respData.contains("deviceIdentifier")) {
                        // 解析 responseData [{"text":"[...]"}] 取内层设备列表文本
                        List<Map<String, Object>> wrapper = objectMapper.readValue(respData, List.class);
                        for (Map<String, Object> item : wrapper) {
                            Object textObj = item.get("text");
                            if (textObj != null) {
                                String innerText = String.valueOf(textObj);
                                if (innerText.contains("deviceIdentifier") || innerText.startsWith("[")) {
                                    // 构建可读的设备列表文本
                                    List<Map<String, Object>> inner = objectMapper.readValue(innerText, List.class);
                                    StringBuilder sb = new StringBuilder("已查询到以下在线设备：\n");
                                    for (Map<String, Object> dev : inner) {
                                        String id = String.valueOf(dev.getOrDefault("deviceIdentifier", ""));
                                        sb.append("- deviceIdentifier: ").append(id).append("\n");
                                    }
                                    return sb.toString();
                                }
                            }
                        }
                    }
                }
            }

            // 其次从 ASSISTANT 消息取自然语言文本
            for (int i = messages.size() - 1; i >= 0; i--) {
                JsonNode msg = messages.get(i);
                if ("ASSISTANT".equals(msg.path("messageType").asText(null))) {
                    String text = msg.path("text").asText(null);
                    if (text != null && !text.isBlank()) return text;
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to extract assistant text: {}", e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> parseDevicesFromState(String nodeOutputStr) {
        List<Map<String, String>> devices = new ArrayList<>();
        try {
            int stateStart = nodeOutputStr.indexOf("state=");
            if (stateStart < 0) return devices;
            int jsonStart = nodeOutputStr.indexOf("{", stateStart);
            if (jsonStart < 0) return devices;
            int jsonEnd = nodeOutputStr.lastIndexOf("}");
            if (jsonEnd <= jsonStart) return devices;
            String stateJson = nodeOutputStr.substring(jsonStart, jsonEnd + 1);
            JsonNode root = objectMapper.readTree(stateJson);
            JsonNode messages = root.at("/OverAllState/data/messages");
            if (!messages.isArray()) return devices;
            for (JsonNode msg : messages) {
                if (!"TOOL".equals(msg.path("messageType").asText(null))) continue;
                JsonNode responses = msg.path("responses");
                if (!responses.isArray()) continue;
                for (JsonNode resp : responses) {
                    String respData = resp.path("responseData").asText(null);
                    if (respData == null || respData.isBlank()) continue;
                    // respData is [{"text":"[...]"}]
                    List<Map<String, Object>> wrapper = objectMapper.readValue(respData, List.class);
                    for (Map<String, Object> item : wrapper) {
                        Object textObj = item.get("text");
                        if (textObj == null) continue;
                        List<Map<String, Object>> inner = objectMapper.readValue(String.valueOf(textObj), List.class);
                        for (Map<String, Object> dev : inner) {
                            String id = String.valueOf(dev.getOrDefault("deviceIdentifier", ""));
                            String name = String.valueOf(dev.getOrDefault("aliyunDeviceName", id));
                            if (!id.isBlank()) {
                                devices.add(Map.of("deviceIdentifier", id, "displayName", name));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to parse device list: {}", e.getMessage());
        }
        return devices;
    }

    /**
     * 解析 publishDeviceCommands 的工具参数。
     * toolArgs 格式: {"arg0":[{"topic":"device/XFK84QW9004B1298C8C4/command","order":"C90102A2010A0602001D"}]}
     * topic 格式: device/{userId}{deviceIdentifier}/command
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> parseActionDetail(String toolArgs, String action, String deviceId) {
        Map<String, String> detail = new LinkedHashMap<>();
        detail.put("type", "open".equalsIgnoreCase(action) || "on".equalsIgnoreCase(action) ? "TURN_ON" : "TURN_OFF");
        if (toolArgs != null) {
            try {
                Map<String, Object> args = objectMapper.readValue(toolArgs, Map.class);
                Object arg0 = args.get("arg0");
                if (arg0 instanceof List) {
                    List<?> list = (List<?>) arg0;
                    if (!list.isEmpty()) {
                        Map<?, ?> first = (Map<?, ?>) list.get(0);
                        Object topicObj = first.get("topic"); String topic = topicObj != null ? String.valueOf(topicObj) : "";
                        Object orderObj = first.get("order"); String order = orderObj != null ? String.valueOf(orderObj) : "";
                        detail.put("order", order);
                        detail.put("topic", topic);
                        // 从 topic 中提取 deviceIdentifier
                        // topic 格式: device/XFK84QW9004B1298C8C4/command
                        if (topic.contains("/")) {
                            String[] parts = topic.split("/");
                            String userIdPart = parts[1]; // "XFK84QW9004B1298C8C4"
                            String id = userIdPart.replaceFirst("^[^0-9A-Za-z]+", "");
                            detail.put("deviceIdentifier", id);
                        } else {
                            detail.put("deviceIdentifier", deviceId != null ? deviceId : "");
                        }
                    } else {
                        detail.put("deviceIdentifier", deviceId != null ? deviceId : "");
                        detail.put("order", "");
                        detail.put("topic", "device/unknown/unknown/command");
                    }
                } else {
                    detail.put("deviceIdentifier", deviceId != null ? deviceId : "");
                    detail.put("order", "");
                    detail.put("topic", "device/unknown/unknown/command");
                }
            } catch (Exception e) {
                logger.warn("Failed to parse toolArgs: {}", toolArgs);
                detail.put("deviceIdentifier", deviceId != null ? deviceId : "");
                detail.put("order", "");
                detail.put("topic", "device/unknown/unknown/command");
            }
        } else {
            detail.put("deviceIdentifier", deviceId != null ? deviceId : "");
            detail.put("order", "");
            detail.put("topic", "device/unknown/unknown/command");
        }
        return detail;
    }

    /**
     * 检测用户操作意图。
     * "开"类词汇优先匹配，除非同时出现"关"类词汇且无"开"类词汇。
     */
    private String detectAction(String message) {
        String lowerMsg = message.toLowerCase();
        boolean hasOn = lowerMsg.matches(".*(开|on|open|启动).*");
        boolean hasOff = lowerMsg.matches(".*(关|off|shutdown|close).*");
        if (hasOn && !hasOff) return "open";
        if (hasOff && !hasOn) return "close";
        if (hasOn && hasOff) return "open"; // 两者都有时优先开
        return "close";
    }
}
