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
import org.happyhai.springai.alibaba.domain.PoemOutput;
import org.happyhai.springai.alibaba.hook.LoggingHook;
import org.happyhai.springai.alibaba.hook.MessageTrimmingHook;
import org.happyhai.springai.alibaba.interceptor.GuardrailInterceptor;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/poem")
public class PoemController {

    private static final Logger logger = LoggerFactory.getLogger(PoemController.class);

    /**
     * 流式对话
     */
    @GetMapping("/stream")
    public String stream(@RequestParam String message) throws GraphRunnerException {
        // 创建 DashScope API 实例
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .build();

        // 创建 ChatModel
        ChatModel chatModel = DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .model("qwen-plus")
                        .build())
                .build();

        LoggingHook loggingHook = new LoggingHook();
        MessageTrimmingHook messageTrimmingHook = new MessageTrimmingHook();

        // 创建 Agent
        ReactAgent agent = ReactAgent.builder()
                .name("my_agent")
                .model(chatModel)
                .outputType(PoemOutput.class)
                .hooks(List.of(loggingHook, messageTrimmingHook))
                .interceptors(new GuardrailInterceptor())
                .build();

        AssistantMessage call = agent.call(message);
        return call.getText();
    }


}
