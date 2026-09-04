package org.happyhai.springai.alibaba.controller;


import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import org.happyhai.springai.alibaba.tool.LightControlTool;
import org.happyhai.springai.alibaba.tool.RecommendActivityTool;
import org.happyhai.springai.alibaba.tool.WeatherTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/agent")
public class AgentController {

    private static final Logger logger = LoggerFactory.getLogger(AgentController.class);
    /**
     * 流式对话（带工具）
     */
    @GetMapping(value = "/stream")
    public Flux<String> stream(@RequestParam String message) throws GraphRunnerException {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        logger.info("🔑 DASHSCOPE_API_KEY: {}", apiKey);
        // 创建 DashScope API 实例
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(apiKey)
                .build();

        // 创建 ChatModel
        ChatModel chatModel = DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .model("qwen-plus")
                        .build())
                .build();

        // 创建 Agent
        ReactAgent agent = ReactAgent.builder()
                .name("my_agent")
                .model(chatModel)
                .build();

        // 流式执行
        logger.info("📡 开始执行 agent.stream()");
        return agent.stream(message)
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
     * 流式对话（带工具）
     */
    @GetMapping(value = "/stream/tool", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> streamWithTool(@RequestParam String message) throws GraphRunnerException {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        logger.info("🔑 DASHSCOPE_API_KEY: {}", apiKey);
        // 创建 DashScope API 实例
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(apiKey)
                .build();

        // 创建 ChatModel
        ChatModel chatModel = DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .model("qwen-plus")
                        .build())
                .build();

        // 创建天气工具
        ToolCallback weatherTool = WeatherTool.create();
        // 创建调光工具
        ToolCallback lightControlTool = LightControlTool.create();
        // 创建推荐活动工具
        ToolCallback recommendActivityTool = RecommendActivityTool.create();

        // 创建人工介入 Hook，用于调光工具需要审批
        HumanInTheLoopHook hitlHook = HumanInTheLoopHook.builder()
                .approvalOn("light_control", ToolConfig.builder()
                        .description("⚠️ 调整灯光亮度是物理操作，请确认是否执行")
                        .build())
                .build();

        // 创建 Agent
        ReactAgent agent = ReactAgent.builder()
                .name("my_agent")
                .model(chatModel)
                .tools(List.of(weatherTool, lightControlTool, recommendActivityTool))
                .hooks(hitlHook)
                .build();

        // 流式执行
        logger.info("📡 开始执行 agent.stream()");
        return agent.stream(message)
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
                            // 检查是否有 toolCalls
                            if (assistantMsg.getToolCalls() != null && !assistantMsg.getToolCalls().isEmpty()) {
                                for (var tc : assistantMsg.getToolCalls()) {
                                    logger.info("💬 AssistantMessage (toolCalls): 工具: {}, 参数: {}", tc.name(), tc.arguments());
                                    // 如果是调光工具，添加待确认状态
                                    if ("light_control".equals(tc.name())) {
                                        try {
                                            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                                            var args = mapper.readValue(tc.arguments(), Map.class);
                                            String lightName = (String) args.get("lightName");
                                            String preset = (String) args.get("preset");
                                            LightControlTool.ManualPendingConfirmation pending = new LightControlTool.ManualPendingConfirmation(lightName, preset);
                                            LightControlTool.addManualConfirmation(pending);
                                            logger.info("✅ 已添加手动待确认: lightName={}, preset={}", lightName, preset);
                                        } catch (Exception e) {
                                            logger.error("解析工具参数失败", e);
                                        }
                                    }
                                }
                                String toolCallsInfo = assistantMsg.getToolCalls().stream()
                                        .map(tc -> "🔧 调用工具: " + tc.name() + ", 参数: " + tc.arguments())
                                        .collect(Collectors.joining("\n"));
                                return "⏳ " + toolCallsInfo;
                            }
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
     * 确认执行调光
     */
    @GetMapping("/light/confirm")
    public Map<String, Object> confirmLight(@RequestParam String lightName, @RequestParam boolean confirm) {
        if (confirm) {
            LightControlTool.clearManualConfirmation(lightName);
            // 执行实际的调光操作
            logger.info("💡 执行调光: {}", lightName);
            return Map.of("success", true, "message", lightName + " 调光已执行");
        } else {
            LightControlTool.clearManualConfirmation(lightName);
            return Map.of("success", true, "message", lightName + " 调光已取消");
        }
    }

    /**
     * 查询待确认的调光请求
     */
    @GetMapping("/light/pending")
    public Map<String, Object> getPendingLight() {
        logger.info("🔍 查询待确认的调光请求, 当前manual pending: {}", LightControlTool.getManualPendingConfirmations());
        // 遍历手动添加的待确认请求
        for (var key : LightControlTool.getManualPendingConfirmations().keySet()) {
            var pending = LightControlTool.getManualConfirmation(key);
            if (pending != null) {
                return Map.of(
                        "hasPending", true,
                        "lightName", pending.lightName(),
                        "preset", pending.preset()
                );
            }
        }
        return Map.of("hasPending", false);
    }
}
