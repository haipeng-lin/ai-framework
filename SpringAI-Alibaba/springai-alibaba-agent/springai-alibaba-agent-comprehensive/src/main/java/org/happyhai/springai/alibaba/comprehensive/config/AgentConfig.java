package org.happyhai.springai.alibaba.comprehensive.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import com.alibaba.cloud.ai.graph.agent.hook.skills.SkillsAgentHook;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.skills.registry.classpath.ClasspathSkillRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.List;

@Configuration
public class AgentConfig {

    private static final Logger logger = LoggerFactory.getLogger(AgentConfig.class);

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
    public MemorySaver memorySaver() {
        return new MemorySaver();
    }

    /**
     * 技能注册中心，从 classpath 加载 SKILL.md
     */
    @Bean
    public ClasspathSkillRegistry classpathSkillRegistry(
            @Value("${agent.react.classpath-path:skills}") String classpathPath) {
        return ClasspathSkillRegistry.builder()
                .classpathPath(classpathPath)
                .build();
    }

    /**
     * 技能钩子，将 SKILL.md 内容注入 Agent 上下文
     */
    @Bean
    public SkillsAgentHook skillsAgentHook(ClasspathSkillRegistry classpathSkillRegistry) {
        return SkillsAgentHook.builder()
                .skillRegistry(classpathSkillRegistry)
                .build();
    }

    /**
     * HITL 钩子，在 publishDeviceCommands 调用前暂停，等待 confirm 授权
     */
    @Bean
    public HumanInTheLoopHook humanInTheLoopHook() {
        return HumanInTheLoopHook.builder()
                .approvalOn("publishDeviceCommands", ToolConfig.builder()
                        .description("设备控制操作需要人工审批确认")
                        .build())
                .build();
    }

    @Bean
    public ReactAgent reactAgent(ChatModel chatModel,
                                 ToolCallbackProvider toolCallbackProvider,
                                 SkillsAgentHook skillsAgentHook,
                                 HumanInTheLoopHook humanInTheLoopHook,
                                 MemorySaver memorySaver,
                                 @Value("${agent.react.name:aquarium_mcp_agent}") String agentName,
                                 @Value("${agent.react.enable:true}") boolean enableLogging) {
        ToolCallback[] mcpTools = toolCallbackProvider.getToolCallbacks();
        logger.info("=== MCP Tools Loaded ===");
        for (ToolCallback tool : mcpTools) {
            logger.info("Tool: {} - {}", tool.getToolDefinition().name(), tool.getToolDefinition().description());
        }

        return ReactAgent.builder()
                .name(agentName)
                .model(chatModel)
                .toolCallbackProviders(toolCallbackProvider)
                .hooks(List.of(skillsAgentHook, humanInTheLoopHook))
                .enableLogging(enableLogging)
                .saver(memorySaver)
                .build();
    }

    @Component
    public static class AgentInitLogger {

        private final String agentName;
        private final String classpathPath;

        public AgentInitLogger(@Value("${agent.react.name:aquarium_mcp_agent}") String agentName,
                               @Value("${agent.react.classpath-path:skills}") String classpathPath) {
            this.agentName = agentName;
            this.classpathPath = classpathPath;
        }

        @PostConstruct
        public void logInit() {
            logger.info("=== Comprehensive Agent Configuration Initialized ===");
            logger.info("Agent: {}", agentName);
            logger.info("Skills path: {}", classpathPath);
            logger.info("Skills: aquarium_light_control");
            logger.info("MCP Server: zetlight-mcp-server");
            logger.info("MCP Tools: getOnlineDevicesByUniqueId, publishDeviceCommands");
            logger.info("HITL Hook: Enabled for publishDeviceCommands operations");
        }
    }
}
