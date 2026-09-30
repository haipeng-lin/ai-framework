package org.happyhai.agentscope.mcp.server.config;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.happyhai.agentscope.mcp.server.tool.TimeTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.List;
import java.util.Map;

@Configuration
public class McpServerConfig {

    private static final Logger log = LoggerFactory.getLogger(McpServerConfig.class);

    /** tools/list 中返回给客户端的 JSON Schema，描述 get_current_time 的入参。 */
    private static final String TIME_TOOL_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "format": {
                  "type": "string",
                  "description": "可选的时间格式，默认为 yyyy-MM-dd HH:mm:ss",
                  "default": "yyyy-MM-dd HH:mm:ss"
                },
                "timezone": {
                  "type": "string",
                  "description": "可选时区，例如 Asia/Shanghai，默认为 Asia/Shanghai",
                  "default": "Asia/Shanghai"
                }
              }
            }
            """;

    @Bean
    public McpJsonMapper mcpJsonMapper() {
        return McpJsonMapper.createDefault();
    }

    @Bean
    public WebMvcStreamableServerTransportProvider mcpTransportProvider(McpJsonMapper jsonMapper,
                                                                        @Value("${agentscope.mcp.endpoint:/mcp}") String endpoint) {
        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(jsonMapper)
                .mcpEndpoint(endpoint)
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> mcpRouterFunction(WebMvcStreamableServerTransportProvider transport) {
        return transport.getRouterFunction();
    }

    @Bean
    public TimeTools timeTools() {
        return new TimeTools();
    }

    @Bean
    public McpServerFeatures.SyncToolSpecification getCurrentTimeTool(TimeTools timeTools,
                                                                      McpJsonMapper jsonMapper) {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("get_current_time")
                .title("Get Current Time")
                .description("获取当前服务器的日期和时间，返回格式可由入参指定，默认 yyyy-MM-dd HH:mm:ss")
                .inputSchema(jsonMapper, TIME_TOOL_SCHEMA)
                .build();

        return new McpServerFeatures.SyncToolSpecification(
                tool,
                (exchange, args) -> {
                    log.info("get_current_time called with args={}", args);
                    String format = stringArg(args, "format");
                    String timezone = stringArg(args, "timezone");
                    var result = timeTools.getCurrentTime(format, timezone);
                    String text = Boolean.TRUE.equals(result.get("ok"))
                            ? "当前时间 (" + result.get("timezone") + "): " + result.get("time")
                            : "错误: " + result.get("error");
                    boolean isError = !Boolean.TRUE.equals(result.get("ok"));
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(text)),
                            isError);
                });
    }

    @Bean(destroyMethod = "close")
    public McpSyncServer mcpSyncServer(WebMvcStreamableServerTransportProvider transport,
                                       McpServerFeatures.SyncToolSpecification getCurrentTimeTool) {
        return McpServer.sync(transport)
                .serverInfo("time-mcp-server", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(getCurrentTimeTool)
                .build();
    }

    private static String stringArg(Map<String, Object> args, String key) {
        if (args == null) {
            return null;
        }
        Object v = args.get(key);
        return v == null ? null : v.toString();
    }
}
