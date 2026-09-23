package org.happyhai.springai.alibaba.light.agent.tools;

import org.happyhai.springai.alibaba.light.agent.domain.PresetRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.BiFunction;

public class SetPresetTool implements BiFunction<PresetRequest, ToolContext, String> {

    private static final Logger log = LoggerFactory.getLogger(SetPresetTool.class);

    private static final java.util.Set<String> VALID_MODES =
            java.util.Set.of("normal", "plant", "reef", "night", "sunrise", "sunset");

    @Override
    public String apply(PresetRequest request, ToolContext context) {
        String mode = request.getMode();
        if (!VALID_MODES.contains(mode.toLowerCase())) {
            return "无效的预设模式: " + mode + "，可选值: " + VALID_MODES;
        }
        log.info("设置预设模式: {}", mode);
        return String.format("已将灯光切换至 [%s] 预设模式", mode);
    }

    public static FunctionToolCallback create() {
        return FunctionToolCallback.builder("set_light_preset", new SetPresetTool())
                .description("设置灯光预设模式，参数 mode 可选：normal（普通）、plant（草缸）、reef（珊瑚）、night（夜间）、sunrise（日出）、sunset（日落）")
                .inputType(PresetRequest.class)
                .build();
    }
}
