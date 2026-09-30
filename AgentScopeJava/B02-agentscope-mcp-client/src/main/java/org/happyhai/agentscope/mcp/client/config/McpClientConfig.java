package org.happyhai.agentscope.mcp.client.config;

import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 通过 AgentScope 2.x 内置的 MCP 客户端连接远端 MCP Server（B01-agentscope-mcp-server），
 * 与服务端约定从 MCP_GATE_TOKEN / MCP_GATEWAY_TOKEN 环境变量读取 Bearer Token。
 */
@Configuration
public class McpClientConfig {

    private static final Logger log = LoggerFactory.getLogger(McpClientConfig.class);

    @Bean(destroyMethod = "close")
    public McpClientWrapper mcpClientWrapper(
            @Value("${agentscope.mcp.url}") String url,
            @Value("${agentscope.mcp.token}") String token,
            @Value("${agentscope.mcp.timeout:PT30S}") Duration timeout,
            @Value("${agentscope.mcp.initialization-timeout:PT10S}") Duration initializationTimeout) {

        McpClientWrapper wrapper = McpClientBuilder.create("time-mcp")
                .streamableHttpTransport(url)
                .header("Authorization", "Bearer " + token)
                .timeout(timeout)
                .initializationTimeout(initializationTimeout)
                .buildAsync()
                .block();

        log.info("Connected to MCP server at {} (initialized={})", url, wrapper.isInitialized());
        return wrapper;
    }
}
