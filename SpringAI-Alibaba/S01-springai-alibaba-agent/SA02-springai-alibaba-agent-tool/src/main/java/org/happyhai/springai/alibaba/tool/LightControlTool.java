package org.happyhai.springai.alibaba.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.chat.model.ToolContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

public class LightControlTool implements BiFunction<LightControlRequest, ToolContext, String> {

    private static final Logger logger = LoggerFactory.getLogger(LightControlTool.class);

    private static final Map<String, PendingConfirmation> PENDING_CONFIRMATIONS = new ConcurrentHashMap<>();

    public static final String CONFIRMATION_PREFIX = "CONFIRM_REQUIRED:";

    public static final Map<String, ManualPendingConfirmation> MANUAL_PENDING_CONFIRMATIONS = new ConcurrentHashMap<>();

    public record ManualPendingConfirmation(String lightName, String preset) {
    }

    public static void addManualConfirmation(ManualPendingConfirmation pending) {
        MANUAL_PENDING_CONFIRMATIONS.put(pending.lightName(), pending);
    }

    public static ManualPendingConfirmation getManualConfirmation(String lightName) {
        return MANUAL_PENDING_CONFIRMATIONS.get(lightName);
    }

    public static Map<String, ManualPendingConfirmation> getManualPendingConfirmations() {
        return MANUAL_PENDING_CONFIRMATIONS;
    }

    public static void clearManualConfirmation(String lightName) {
        MANUAL_PENDING_CONFIRMATIONS.remove(lightName);
    }

    public static void confirm(String lightName) {
        PendingConfirmation pending = PENDING_CONFIRMATIONS.remove(lightName);
        if (pending != null) {
            executeLightControl(pending.preset, pending.lightName);
        }
    }

    public static PendingConfirmation getPendingConfirmation(String lightName) {
        return PENDING_CONFIRMATIONS.get(lightName);
    }

    public static Map<String, PendingConfirmation> getPendingConfirmations() {
        return PENDING_CONFIRMATIONS;
    }

    public static void clearPendingConfirmation(String lightName) {
        PENDING_CONFIRMATIONS.remove(lightName);
    }

    private static void executeLightControl(String preset, String lightName) {
        logger.info("💡 执行调光预设: 灯名称={}, 预设={}", lightName, preset);
        // 实际执行调光逻辑
        logger.info("💡 调光完成: {} 已设置为预设 {}", lightName, preset);
    }

    @Override
    public String apply(LightControlRequest request, ToolContext context) {
        String lightName = request.getLightName();
        String preset = request.getPreset();

        logger.info("💡 调光工具被调用！灯名称: {}, 预设: {}", lightName, preset);
        logger.info("🔍 当前pending确认: {}", PENDING_CONFIRMATIONS);

        // 创建待确认的任务
        PENDING_CONFIRMATIONS.put(lightName, new PendingConfirmation(lightName, preset));
        logger.info("✅ 已添加待确认请求: {}", lightName);

        return CONFIRMATION_PREFIX + "确认将 " + lightName + " 设置为 " + preset + " 预设吗？";
    }

    public record PendingConfirmation(String lightName, String preset) {
    }

    public static ToolCallback create() {
        return FunctionToolCallback.builder("light_control", new LightControlTool())
                .description("控制智能灯光，支持设置预设模式（如：阅读模式、影院模式、睡眠模式、办公模式）")
                .inputType(LightControlRequest.class)
                .build();
    }
}
