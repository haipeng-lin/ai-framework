package org.happyhai.springai.alibaba.loop.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import jakarta.annotation.PostConstruct;
import org.happyhai.springai.alibaba.loop.service.TraceInfoService;
import org.happyhai.springai.alibaba.loop.tool.AquariumLightTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.List;

@Configuration
public class AgentConfig {

    private static final Logger logger = LoggerFactory.getLogger(AgentConfig.class);

    private final TraceInfoService traceInfoService;

    public AgentConfig(TraceInfoService traceInfoService) {
        this.traceInfoService = traceInfoService;
    }

    @Bean
    public DashScopeApi dashScopeApi() {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            logger.warn("DASHSCOPE_API_KEY environment variable is not set");
        } else {
            logger.info("DASHSCOPE_API_KEY is configured");
        }
        return DashScopeApi.builder()
                .apiKey(apiKey != null ? apiKey : "")
                .build();
    }

    @Bean
    public ChatModel chatModel(DashScopeApi dashScopeApi) {
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .model("qwen-plus")
                        .build())
                .build();
    }

    @Bean
    public ToolCallback aquariumLightTool() {
        return AquariumLightTool.create(traceInfoService);
    }

    @Bean
    public HumanInTheLoopHook humanInTheLoopHook() {
        return HumanInTheLoopHook.builder()
                .approvalOn("aquarium_light_control", ToolConfig.builder()
                        .description("水族灯调光操作需要人工审批确认")
                        .build())
                .build();
    }

    @Bean
    public MemorySaver memorySaver() {
        return new MemorySaver();
    }

    @Bean
    public ReactAgent reactAgent(ChatModel chatModel, 
                                  ToolCallback aquariumLightTool,
                                  HumanInTheLoopHook humanInTheLoopHook,
                                  MemorySaver memorySaver) {
        return ReactAgent.builder()
                .name("aquarium_light_agent")
                .model(chatModel)
                .tools(List.of(aquariumLightTool))
                .hooks(humanInTheLoopHook)
                .saver(memorySaver)
                .build();
    }

    @Component
    public static class AgentInitLogger {

        @PostConstruct
        public void logInit() {
            logger.info("=== Human-in-the-Loop Agent Configuration Initialized ===");
            logger.info("Agent: aquarium_light_agent");
            logger.info("Tool: aquarium_light_control (水族灯调光)");
            logger.info("HITL Hook: Enabled for aquarium_light_control operations");
        }
    }
}
