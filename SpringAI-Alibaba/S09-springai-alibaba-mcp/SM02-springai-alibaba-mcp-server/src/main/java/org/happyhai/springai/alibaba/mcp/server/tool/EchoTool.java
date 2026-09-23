package org.happyhai.springai.alibaba.mcp.server.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 回声工具 —— 测试最基础的入参回传。
 */
@Component
public class EchoTool {

    @Tool(description = "把传入的消息原样回显,常用于连通性测试。")
    public String echo(@ToolParam(description = "要回显的文本") String message) {
        return "echo: " + message;
    }

    @Tool(description = "把传入的消息按指定次数重复。")
    public String repeat(
            @ToolParam(description = "要重复的文本") String message,
            @ToolParam(description = "重复次数,必须 >= 1") int times) {
        if (times < 1) {
            return "echo.repeat: times must be >= 1";
        }
        return "echo.repeat: " + String.valueOf(message).repeat(times);
    }
}