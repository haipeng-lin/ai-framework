package org.happyhai.springai.alibaba.mcp.server.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 基础算术工具 —— 测试多参数传递。
 */
@Component
public class MathTool {

    @Tool(description = "两数相加,返回 a + b。")
    public double add(
            @ToolParam(description = "加数 a") double a,
            @ToolParam(description = "加数 b") double b) {
        return a + b;
    }

    @Tool(description = "两数相减,返回 a - b。")
    public double subtract(
            @ToolParam(description = "被减数 a") double a,
            @ToolParam(description = "减数 b") double b) {
        return a - b;
    }

    @Tool(description = "两数相乘,返回 a * b。")
    public double multiply(
            @ToolParam(description = "乘数 a") double a,
            @ToolParam(description = "乘数 b") double b) {
        return a * b;
    }
}