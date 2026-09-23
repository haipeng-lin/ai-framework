package org.happyhai.springai.alibaba.loop.tool;

import org.happyhai.springai.alibaba.loop.service.TraceInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.chat.model.ToolContext;

import java.util.function.BiFunction;

public class AquariumLightTool implements BiFunction<AquariumLightRequest, ToolContext, String> {

    private static final Logger logger = LoggerFactory.getLogger(AquariumLightTool.class);
    private static final String CONFIRMATION_PREFIX = "[PENDING_CONFIRM]";

    private final TraceInfoService traceInfoService;

    public AquariumLightTool(TraceInfoService traceInfoService) {
        this.traceInfoService = traceInfoService;
    }

    @Override
    public String apply(AquariumLightRequest request, ToolContext context) {
        String lightName = request.getLightName();
        String preset = request.getPreset();
        Integer brightness = request.getBrightness();

        logger.info("水族灯调光工具被调用 - 灯名称: {}, 预设: {}, 亮度: {}%", lightName, preset, brightness);

        String traceId = null;
        if (context != null && context.getContext() != null) {
            traceId = (String) context.getContext().get("traceId");
        }

        if (traceId != null && traceInfoService != null) {
            traceInfoService.updateLightParams(traceId, lightName, preset, brightness);
            logger.info("已更新 TraceInfo 参数 - traceId: {}", traceId);
        }

        StringBuilder response = new StringBuilder();
        response.append(CONFIRMATION_PREFIX).append("\n");
        response.append("水族灯调光操作待确认:\n");
        response.append("- 灯名称: ").append(lightName).append("\n");
        response.append("- 预设模式: ").append(preset).append("\n");
        response.append("- 亮度: ").append(brightness).append("%");
        if (traceId != null) {
            response.append("\n- TraceId: ").append(traceId);
        }
        response.append("\n请通过确认接口确认或取消此操作。");

        return response.toString();
    }

    public static void executeLightControl(String lightName, String preset, Integer brightness) {
        logger.info("执行水族灯调光 - 灯名称: {}, 预设: {}, 亮度: {}%", lightName, preset, brightness);
        logger.info("虚拟水族灯调光操作完成");
    }

    public static FunctionToolCallback create(TraceInfoService traceInfoService) {
        return FunctionToolCallback.builder("aquarium_light_control", new AquariumLightTool(traceInfoService))
                .description("控制水族箱智能灯光，支持设置预设模式（日出、日落、夜间、珊瑚生长、全开）和亮度百分比（0-100）")
                .inputType(AquariumLightRequest.class)
                .build();
    }
}
