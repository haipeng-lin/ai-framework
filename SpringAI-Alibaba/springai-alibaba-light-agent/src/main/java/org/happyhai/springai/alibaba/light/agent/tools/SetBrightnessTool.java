package org.happyhai.springai.alibaba.light.agent.tools;

import org.happyhai.springai.alibaba.light.agent.domain.BrightnessRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.BiFunction;

public class SetBrightnessTool implements BiFunction<BrightnessRequest, ToolContext, String> {

    private static final Logger log = LoggerFactory.getLogger(SetBrightnessTool.class);

    @Override
    public String apply(BrightnessRequest request, ToolContext context) {
        int brightness = request.getBrightness();
        if (brightness < 0 || brightness > 100) {
            return "亮度值必须在 0-100 之间，当前值 " + brightness + " 超出范围。";
        }
        log.info("设置亮度: {}%", brightness);
        return String.format("已将灯光亮度设置为 %d%%", brightness);
    }

    public static FunctionToolCallback create() {
        return FunctionToolCallback.builder("set_light_brightness", new SetBrightnessTool())
                .description("设置灯具亮度，参数 brightness 取值 0-100")
                .inputType(BrightnessRequest.class)
                .build();
    }
}
