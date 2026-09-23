package org.happyhai.springai.alibaba.light.agent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.BiFunction;

public class DiagnoseConnectivityTool implements BiFunction<Void, ToolContext, String> {

    private static final Logger log = LoggerFactory.getLogger(DiagnoseConnectivityTool.class);

    @Override
    public String apply(Void unused, ToolContext context) {
        log.info("诊断设备连接状态");
        return "设备连接状态：在线，信号强度良好（-52 dBm），延迟 23ms，MQTT 连接正常";
    }

    public static FunctionToolCallback create() {
        return FunctionToolCallback.builder("diagnose_connectivity", new DiagnoseConnectivityTool())
                .description("诊断设备连接状态，返回设备在线状态和信号强度")
                .inputType(Void.class)
                .build();
    }
}
