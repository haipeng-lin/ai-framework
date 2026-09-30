package org.happyhai.agentscope.mcp.client.controller;

import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 暴露 MCP 工具给外部调用，结构与 A04-agentscope-tool 的 ToolController 保持一致。
 * 工具的真实实现在远端 B01-agentscope-mcp-server，Toolkit 充当本地视图；
 * 调用时直接走 McpClientWrapper，绕开本地 ToolExecutor 对输入的 JSON-Schema 预校验。
 */
@RestController
@RequestMapping("/mcp-tools")
public class McpToolController {

    private static final Logger log = LoggerFactory.getLogger(McpToolController.class);

    private final Toolkit toolkit;
    private final McpClientWrapper mcpClientWrapper;

    public McpToolController(Toolkit toolkit, McpClientWrapper mcpClientWrapper) {
        this.toolkit = toolkit;
        this.mcpClientWrapper = mcpClientWrapper;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return toolkit.getToolSchemas().stream()
                .map(McpToolController::toView)
                .toList();
    }

    @PostMapping("/{name}/invoke")
    public Map<String, Object> invoke(@PathVariable("name") String name,
                                      @RequestBody Map<String, Object> input) {
        if (toolkit.getTool(name) == null) {
            return Map.of(
                    "ok", false,
                    "error", "tool not found: " + name,
                    "available", toolkit.getToolNames()
            );
        }

        String callId = UUID.randomUUID().toString();
        log.debug("Invoking MCP tool '{}' with input={} (callId={})", name, input, callId);

        McpSchema.CallToolResult result = mcpClientWrapper.callTool(name, input).block();
        if (result == null) {
            return Map.of(
                    "ok", false,
                    "error", "mcp call returned null",
                    "name", name,
                    "callId", callId);
        }

        List<String> texts = result.content() == null
                ? List.of()
                : result.content().stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(b -> ((McpSchema.TextContent) b).text())
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", !Boolean.TRUE.equals(result.isError()));
        response.put("name", name);
        response.put("callId", callId);
        response.put("isError", result.isError());
        response.put("output", texts);
        return response;
    }

    private static Map<String, Object> toView(ToolSchema schema) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("name", schema.getName());
        view.put("description", schema.getDescription());
        view.put("parameters", schema.getParameters());
        return view;
    }
}
