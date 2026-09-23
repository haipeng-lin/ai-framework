package org.happyhai.springai.alibaba.light.agent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.BiFunction;

public class QueryDeviceStatusTool implements BiFunction<Void, ToolContext, String> {

    private static final Logger log = LoggerFactory.getLogger(QueryDeviceStatusTool.class);

    @Override
    public String apply(Void unused, ToolContext context) {
        log.info("查询设备完整状态");
        return "当前状态：亮度 75%，色温 4500K，水温 26.5°C，当前预设：草缸模式，设备在线";
    }

    public static FunctionToolCallback create() {
        return FunctionToolCallback.builder("query_device_status", new QueryDeviceStatusTool())
                .description("查询设备当前完整状态，返回水温、亮度、色温、当前预设模式等信息")
                .inputType(Void.class)
                .build();
    }
}
