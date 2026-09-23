package org.happyhai.springai.alibaba.mcp.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 启动后自动跑一遍测试,验证 OAuth2 + MCP 链路。
 * <p>
 * 跑通就说明:
 * <ul>
 *   <li>SM01 能正常签发 client_credentials access token</li>
 *   <li>SM03 能拿到 token 并注入到 MCP 请求头</li>
 *   <li>SM02 的 mcp-server-security 能用 SM01 的 JWKS 校验 token</li>
 *   <li>SM02 的工具能正常被远端调用并返回结果</li>
 * </ul>
 */
@Component
public class McpClientTestRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(McpClientTestRunner.class);

    private final ToolCallbackProvider toolCallbackProvider;

    public McpClientTestRunner(ToolCallbackProvider toolCallbackProvider) {
        this.toolCallbackProvider = toolCallbackProvider;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("==================== MCP 端到端测试开始 ====================");

        // 1. 列出工具
        ToolCallback[] tools = toolCallbackProvider.getToolCallbacks();
        log.info("[1] 工具列表(共 {} 个):", tools.length);
        for (ToolCallback t : tools) {
            log.info("    - {} : {}", t.getToolDefinition().name(), t.getToolDefinition().description());
        }
        if (tools.length == 0) {
            log.error("未发现任何工具 —— 链路可能未通");
            return;
        }

        // 2. 调用 echo
        callTool("echo", Map.of("message", "hello from SM03"));

        // 3. 调用 echo.repeat
        callTool("repeat", Map.of("message", "ab", "times", 3));

        // 4. 调用 add
        callTool("add", Map.of("a", 1.5, "b", 2.25));

        // 5. 调用 listModes
        callTool("listModes", Map.of());

        // 6. 调用 controlLight
        callTool("controlLight",
                Map.of("deviceId", "aquarium-1", "mode", "日落", "brightness", 30));

        // 7. 调用 getLightStatus
        callTool("getLightStatus", Map.of("deviceId", "aquarium-1"));

        log.info("==================== MCP 端到端测试结束 ====================");
    }

    private void callTool(String name, Map<String, Object> args) {
        ToolCallback[] tools = toolCallbackProvider.getToolCallbacks();
        for (ToolCallback t : tools) {
            if (t.getToolDefinition().name().equals(name)) {
                log.info("[call] {} args={}", name, args);
                try {
                    String result = t.call(String.valueOf(args));
                    log.info("[result] {} -> {}", name, result);
                } catch (Exception e) {
                    log.error("[error] {} 调用失败: {}", name, e.getMessage(), e);
                }
                return;
            }
        }
        log.warn("[skip] 没找到工具 {}", name);
    }
}