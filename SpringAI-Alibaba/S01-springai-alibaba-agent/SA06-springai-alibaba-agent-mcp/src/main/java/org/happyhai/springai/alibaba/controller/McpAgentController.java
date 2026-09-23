package org.happyhai.springai.alibaba.controller;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/mcp-agent")
public class McpAgentController {

    private static final Logger logger = LoggerFactory.getLogger(McpAgentController.class);

    @Autowired
    private ToolCallbackProvider toolCallbackProvider;

    /**
     * 流式对话（通过MCP工具调用）
     */
    @GetMapping(value = "/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> stream(@RequestParam String message) throws Exception {
        logger.info("开始MCP流式对话, 用户消息: {}", message);
        
        // 1、创建 DashScope API 实例
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .build();

        // 2、创建 ChatModel
        ChatModel chatModel = DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .model("qwen-plus")
                        .build())
                .build();

        // 3、获取MCP服务端暴露的工具方法
        ToolCallback[] toolCallbacks = toolCallbackProvider.getToolCallbacks();

        // 4、创建 Agent
        ReactAgent agent = ReactAgent.builder()
                .name("mcp_agent")
                .model(chatModel)
                .toolCallbackProviders(toolCallbackProvider)
                .build();

        // 5、流式执行
        logger.info("开始执行 agent.stream()");
        return agent.stream(message)
                .doOnSubscribe(s -> logger.info("客户端已订阅流式响应"))
                .doOnNext(output -> logger.info("收到输出: {}", output.getClass().getSimpleName()))
                .filter(output -> output instanceof NodeOutput)
                .map(output -> {
                    var nodeOutput = (NodeOutput) output;
                    if (nodeOutput instanceof StreamingOutput streamingOutput) {
                        Message msg = streamingOutput.message();
                        if (msg == null) {
                            logger.debug("消息为空，跳过");
                            return "";
                        }
                        logger.info("消息类型: {}, 内容: {}", msg.getClass().getSimpleName(), msg);
                        if (msg instanceof AssistantMessage assistantMsg) {
                            if (assistantMsg.getToolCalls() != null && !assistantMsg.getToolCalls().isEmpty()) {
                                String toolCallsInfo = assistantMsg.getToolCalls().stream()
                                        .map(tc -> "调用工具: " + tc.name() + ", 参数: " + tc.arguments())
                                        .collect(Collectors.joining("\n"));
                                return toolCallsInfo;
                            }
                            String text = assistantMsg.getText();
                            logger.info("AssistantMessage: {}", text);
                            return text;
                        } else if (msg instanceof ToolResponseMessage toolMsg) {
                            String text = "工具返回: " + toolMsg.getText();
                            logger.info("ToolResponseMessage: {}", text);
                            return text;
                        }
                    }
                    return "";
                })
                .doOnError(e -> logger.error("流式处理出错", e))
                .doOnComplete(() -> logger.info("流式响应完成"))
                .filter(text -> !text.isEmpty());
    }

    /**
     * 获取可用的MCP工具列表
     */
    @GetMapping("/tools")
    public List<ToolInfo> getTools() {
        ToolCallback[] toolCallbacks = toolCallbackProvider.getToolCallbacks();

        return java.util.Arrays.stream(toolCallbacks)
                .map(tool -> {
                    ToolDefinition def = tool.getToolDefinition();
                    return new ToolInfo(def.name(), def.description());
                })
                .collect(Collectors.toList());
    }

    public record ToolInfo(String name, String description) {}
}
