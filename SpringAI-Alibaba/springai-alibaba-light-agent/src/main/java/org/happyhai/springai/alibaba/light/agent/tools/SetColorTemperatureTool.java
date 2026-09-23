package org.happyhai.springai.alibaba.light.agent.tools;

import org.happyhai.springai.alibaba.light.agent.domain.ColorTemperatureRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.BiFunction;

public class SetColorTemperatureTool implements BiFunction<ColorTemperatureRequest, ToolContext, String> {

    private static final Logger log = LoggerFactory.getLogger(SetColorTemperatureTool.class);

    @Override
    public String apply(ColorTemperatureRequest request, ToolContext context) {
        int temperature = request.getTemperature();
        if (temperature < 2700 || temperature > 6500) {
            return "色温值必须在 2700K-6500K 之间，当前值 " + temperature + "K 超出范围。";
        }
        log.info("设置色温: {}K", temperature);
        return String.format("已将灯光色温设置为 %dK", temperature);
    }

    public static FunctionToolCallback create() {
        return FunctionToolCallback.builder("set_light_color_temperature", new SetColorTemperatureTool())
                .description("设置灯具色温，参数 temperature 单位为 K，取值 2700-6500")
                .inputType(ColorTemperatureRequest.class)
                .build();
    }
}
