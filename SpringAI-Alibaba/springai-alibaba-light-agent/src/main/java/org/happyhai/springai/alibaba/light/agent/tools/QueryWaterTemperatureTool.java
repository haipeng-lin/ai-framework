package org.happyhai.springai.alibaba.light.agent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.BiFunction;

public class QueryWaterTemperatureTool implements BiFunction<Void, ToolContext, String> {

    private static final Logger log = LoggerFactory.getLogger(QueryWaterTemperatureTool.class);

    @Override
    public String apply(Void unused, ToolContext context) {
        log.info("查询水温");
        return "当前水温：26.5°C";
    }

    public static FunctionToolCallback create() {
        return FunctionToolCallback.builder("query_water_temperature", new QueryWaterTemperatureTool())
                .description("查询当前水温")
                .inputType(Void.class)
                .build();
    }
}
