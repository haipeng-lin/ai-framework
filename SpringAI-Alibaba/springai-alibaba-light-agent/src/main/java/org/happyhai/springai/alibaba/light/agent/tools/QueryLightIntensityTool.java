package org.happyhai.springai.alibaba.light.agent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.BiFunction;

public class QueryLightIntensityTool implements BiFunction<Void, ToolContext, String> {

    private static final Logger log = LoggerFactory.getLogger(QueryLightIntensityTool.class);

    @Override
    public String apply(Void unused, ToolContext context) {
        log.info("查询光照强度");
        return "当前光照强度：75%（PAR 值约 280 µmol/m²/s）";
    }

    public static FunctionToolCallback create() {
        return FunctionToolCallback.builder("query_light_intensity", new QueryLightIntensityTool())
                .description("查询当前光照强度")
                .inputType(Void.class)
                .build();
    }
}
