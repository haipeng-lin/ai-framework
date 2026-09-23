package org.happyhai.springai.alibaba.controller;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import org.happyhai.springai.alibaba.hook.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 记忆功能测试 Controller
 */
@RestController
@RequestMapping("/memory")
public class MemoryController {

    private static final Logger logger = LoggerFactory.getLogger(MemoryController.class);

    private final Map<String, ReactAgent> agents = new ConcurrentHashMap<>();
    private final RedisSaver redisSaver;

    public MemoryController(RedisSaver redisSaver) {
        this.redisSaver = redisSaver;
    }

    private ChatModel createChatModel() {
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .build();
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .model("qwen-plus")
                        .build())
                .build();
    }

    /**
     * 基础记忆 - 使用 RedisSaver 保持对话上下文
     */
    @GetMapping("/basic")
    public Map<String, Object> basicMemory(
            @RequestParam String message,
            @RequestParam(defaultValue = "1") String threadId) {
        try {
            String agentKey = "basic_" + threadId;
            ReactAgent agent = agents.computeIfAbsent(agentKey, k -> {
                ChatModel chatModel = createChatModel();
                return ReactAgent.builder()
                        .name("basic_agent")
                        .model(chatModel)
                        .saver(redisSaver)
                        .build();
            });

            RunnableConfig config = RunnableConfig.builder()
                    .threadId(threadId)
                    .build();

            AssistantMessage response = agent.call(message, config);

            return Map.of(
                    "threadId", threadId,
                    "response", response.getText()
            );
        } catch (Exception e) {
            logger.error("执行失败: {}", e.getMessage(), e);
            return Map.of(
                    "error", true,
                    "message", e.getMessage()
            );
        }
    }

    /**
     * 消息修剪 - 限制保留的消息数量
     */
    @GetMapping("/trim")
    public Map<String, Object> messageTrimming(
            @RequestParam String message,
            @RequestParam(defaultValue = "1") String threadId) {
        try {
            String agentKey = "trim_" + threadId;
            ReactAgent agent = agents.computeIfAbsent(agentKey, k -> {
                ChatModel chatModel = createChatModel();
                return ReactAgent.builder()
                        .name("trim_agent")
                        .model(chatModel)
                        .hooks(new MessageTrimmingHook())
                        .saver(redisSaver)
                        .build();
            });

            RunnableConfig config = RunnableConfig.builder()
                    .threadId(threadId)
                    .build();

            AssistantMessage response = agent.call(message, config);

            return Map.of(
                    "threadId", threadId,
                    "response", response.getText()
            );
        } catch (Exception e) {
            logger.error("执行失败: {}", e.getMessage(), e);
            return Map.of("error", true, "message", e.getMessage());
        }
    }

    /**
     * 消息删除 - 删除旧消息
     */
    @GetMapping("/delete")
    public Map<String, Object> messageDeletion(
            @RequestParam String message,
            @RequestParam(defaultValue = "1") String threadId) {
        try {
            String agentKey = "delete_" + threadId;
            ReactAgent agent = agents.computeIfAbsent(agentKey, k -> {
                ChatModel chatModel = createChatModel();
                return ReactAgent.builder()
                        .name("delete_agent")
                        .model(chatModel)
                        .systemPrompt("请简洁明了。")
                        .hooks(new MessageDeletionHook())
                        .saver(redisSaver)
                        .build();
            });

            RunnableConfig config = RunnableConfig.builder()
                    .threadId(threadId)
                    .build();

            AssistantMessage response = agent.call(message, config);

            return Map.of(
                    "threadId", threadId,
                    "response", response.getText()
            );
        } catch (Exception e) {
            logger.error("执行失败: {}", e.getMessage(), e);
            return Map.of("error", true, "message", e.getMessage());
        }
    }

    /**
     * 消息总结 - 使用 LLM 总结历史消息
     */
    @GetMapping("/summarize")
    public Map<String, Object> messageSummarization(
            @RequestParam String message,
            @RequestParam(defaultValue = "1") String threadId) {
        try {
            String agentKey = "summarize_" + threadId;
            ReactAgent agent = agents.computeIfAbsent(agentKey, k -> {
                ChatModel chatModel = createChatModel();
                // 使用更便宜的模型进行总结
                ChatModel summaryModel = DashScopeChatModel.builder()
                        .dashScopeApi(DashScopeApi.builder()
                                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                                .build())
                        .defaultOptions(DashScopeChatOptions.builder()
                                .model("qwen-turbo")
                                .build())
                        .build();

                return ReactAgent.builder()
                        .name("summarize_agent")
                        .model(chatModel)
                        .hooks(new MessageSummarizationHook(summaryModel, 1000, 2))
                        .saver(redisSaver)
                        .build();
            });

            RunnableConfig config = RunnableConfig.builder()
                    .threadId(threadId)
                    .build();

            AssistantMessage response = agent.call(message, config);

            return Map.of(
                    "threadId", threadId,
                    "response", response.getText()
            );
        } catch (Exception e) {
            logger.error("执行失败: {}", e.getMessage(), e);
            return Map.of("error", true, "message", e.getMessage());
        }
    }

    /**
     * 验证响应 - 检查敏感词
     */
    @GetMapping("/validate")
    public Map<String, Object> validateResponse(
            @RequestParam String message,
            @RequestParam(defaultValue = "1") String threadId) throws GraphRunnerException {

        String agentKey = "validate_" + threadId;
        ReactAgent agent = agents.computeIfAbsent(agentKey, k -> {
            ChatModel chatModel = createChatModel();
            return ReactAgent.builder()
                    .name("validate_agent")
                    .model(chatModel)
                    .hooks(new ValidateResponseHook())
                    .saver(redisSaver)
                    .build();
        });

        RunnableConfig config = RunnableConfig.builder()
                .threadId(threadId)
                .build();

        AssistantMessage response = agent.call(message, config);

        return Map.of(
                "threadId", threadId,
                "response", response.getText()
        );
    }

    /**
     * 流式对话 - 带记忆
     */
    @GetMapping(value = "/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> stream(
            @RequestParam String message,
            @RequestParam(defaultValue = "1") String threadId) throws GraphRunnerException {

        ChatModel chatModel = createChatModel();

        ReactAgent agent = ReactAgent.builder()
                .name("stream_agent")
                .model(chatModel)
                .hooks(new MessageTrimmingHook())
                .saver(redisSaver)
                .build();

        RunnableConfig config = RunnableConfig.builder()
                .threadId(threadId)
                .build();

        return agent.stream(message,config)
                .doOnSubscribe(s -> logger.info("📥 客户端已订阅流式响应"))
                .doOnNext(output -> logger.info("📩 收到输出: {}", output.getClass().getSimpleName()))
                .filter(output -> output instanceof NodeOutput)
                .map(output -> {
                    var nodeOutput = (NodeOutput) output;
                    if (nodeOutput instanceof StreamingOutput streamingOutput) {
                        Message msg = streamingOutput.message();
                        if (msg == null) {
                            logger.debug("📨 消息为空，跳过");
                            return "";
                        }
                        logger.info("📨 消息类型: {}, 内容: {}", msg.getClass().getSimpleName(), msg);
                        if (msg instanceof AssistantMessage assistantMsg) {
                            String text = assistantMsg.getText();
                            logger.info("💬 AssistantMessage: {}", text);
                            return text;
                        } else if (msg instanceof ToolResponseMessage toolMsg) {
                            String text = "🔧 工具返回: " + toolMsg.getText();
                            logger.info("🔧 ToolResponseMessage: {}", text);
                            return text;
                        }
                    }
                    return "";
                })
                .doOnError(e -> logger.error("❌ 流式处理出错", e))
                .doOnComplete(() -> logger.info("✅ 流式响应完成"))
                .filter(text -> !text.isEmpty());
    }

    /**
     * 清除会话记忆
     */
    @DeleteMapping("/clear/{threadId}")
    public Map<String, Object> clearMemory(@PathVariable String threadId) {
        // 移除指定 threadId 的 agent 实例
        agents.entrySet().removeIf(entry -> entry.getKey().endsWith(threadId));
        return Map.of(
                "success", true,
                "message", "会话 " + threadId + " 的记忆已清除"
        );
    }

    /**
     * 获取当前活跃的会话列表
     */
    @GetMapping("/sessions")
    public Map<String, Object> listSessions() {
        return Map.of(
                "sessions", agents.keySet().stream().toList(),
                "count", agents.size()
        );
    }
}
