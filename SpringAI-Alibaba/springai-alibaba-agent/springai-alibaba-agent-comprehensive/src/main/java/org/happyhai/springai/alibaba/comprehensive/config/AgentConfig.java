package org.happyhai.springai.alibaba.comprehensive.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import jakarta.annotation.PostConstruct;
import org.happyhai.springai.alibaba.comprehensive.service.DeviceService;
import org.happyhai.springai.alibaba.comprehensive.service.TraceInfoService;
import org.happyhai.springai.alibaba.comprehensive.tool.DeviceQueryTool;
import org.happyhai.springai.alibaba.comprehensive.tool.LightControlTool;
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

    private final DeviceService deviceService;
    private final TraceInfoService traceInfoService;

    public AgentConfig(DeviceService deviceService, TraceInfoService traceInfoService) {
        this.deviceService = deviceService;
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
    public ToolCallback deviceQueryTool() {
        return DeviceQueryTool.create(deviceService);
    }

    @Bean
    public ToolCallback lightControlTool() {
        return LightControlTool.create(deviceService, traceInfoService);
    }

    @Bean
    public HumanInTheLoopHook humanInTheLoopHook() {
        return HumanInTheLoopHook.builder()
                .approvalOn("light_control", ToolConfig.builder()
                        .description("灯光控制操作需要人工审批确认")
                        .build())
                .build();
    }

    @Bean
    public MemorySaver memorySaver() {
        return new MemorySaver();
    }

    @Bean
    public ReactAgent reactAgent(ChatModel chatModel, 
                                 ToolCallback deviceQueryTool,
                                 ToolCallback lightControlTool,
                                 HumanInTheLoopHook humanInTheLoopHook,
                                 MemorySaver memorySaver) {
        return ReactAgent.builder()
                .name("aquarium_comprehensive_agent")
                .model(chatModel)
                .tools(List.of(deviceQueryTool, lightControlTool))
                .hooks(humanInTheLoopHook)
                .saver(memorySaver)
                .build();
    }

    @Component
    public static class AgentInitLogger {

        @PostConstruct
        public void logInit() {
            logger.info("=== Comprehensive Agent Configuration Initialized ===");
            logger.info("Agent: aquarium_comprehensive_agent");
            logger.info("Tools: device_query, light_control");
            logger.info("HITL Hook: Enabled for light_control operations");
        }
    }
}
