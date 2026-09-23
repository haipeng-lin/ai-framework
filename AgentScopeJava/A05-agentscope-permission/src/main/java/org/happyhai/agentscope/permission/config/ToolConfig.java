package org.happyhai.agentscope.permission.config;

import io.agentscope.core.tool.Toolkit;
import org.happyhai.agentscope.permission.tool.DeviceTools;
import org.happyhai.agentscope.permission.tool.LightTools;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolConfig {

    @Bean
    public Toolkit toolkit() {
        Toolkit toolkit = new Toolkit();
        // 注解形式的轻量写法：@Tool + @ToolParam 由框架反射解析
        toolkit.registerTool(new DeviceTools());
        toolkit.registerTool(new LightTools());
        return toolkit;
    }

}