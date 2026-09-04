// 文件名: WeatherTool.java
package org.happyhai.springai.alibaba.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.chat.model.ToolContext;

import java.util.function.BiFunction;

// 修改泛型，第一个参数从 String 变为 WeatherRequest
public class WeatherTool implements BiFunction<WeatherRequest, ToolContext, String> {

    private static final Logger logger = LoggerFactory.getLogger(WeatherTool.class);

    @Override
    public String apply(WeatherRequest request, ToolContext context) {
        // 从 request 对象中获取城市名称
        String city = request.getCity();
        logger.info("🌤️ 天气工具被调用了！查询参数: {}", city);

        // 简单的天气查询实现
        String result = "天气查询结果: " + city + " - 晴转多云，25°C";
        logger.info("🌤️ 天气工具返回结果: {}", result);
        return result;
    }

    /**
     * 创建天气工具回调
     */
    public static ToolCallback create() {
        return FunctionToolCallback.builder("weather", new WeatherTool())
                .description("获取指定城市的天气信息")
                // 明确指定输入参数的类型为 WeatherRequest.class
                .inputType(WeatherRequest.class)
                .build();
    }
}