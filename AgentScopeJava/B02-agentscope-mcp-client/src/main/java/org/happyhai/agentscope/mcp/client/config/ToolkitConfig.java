package org.happyhai.agentscope.mcp.client.config;

import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 把 MCP 客户端注册进 Toolkit，工具列表来自 B01 远端，与 A04 中本地 SimpleTools 等价。
 */
@Configuration
public class ToolkitConfig {

    private static final Logger log = LoggerFactory.getLogger(ToolkitConfig.class);

    @Bean
    public Toolkit toolkit(McpClientWrapper mcpClientWrapper) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerMcpClient(mcpClientWrapper).block();
        log.info("MCP tools registered: {}", toolkit.getToolNames());
        return toolkit;
    }
}
