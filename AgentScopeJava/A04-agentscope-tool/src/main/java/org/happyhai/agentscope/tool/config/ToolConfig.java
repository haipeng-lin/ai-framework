package org.happyhai.agentscope.tool.config;

import io.agentscope.core.tool.Toolkit;
import org.happyhai.agentscope.tool.tool.SimpleTools;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolConfig {

    @Bean
    public Toolkit toolkit() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new SimpleTools());
        return toolkit;
    }

}