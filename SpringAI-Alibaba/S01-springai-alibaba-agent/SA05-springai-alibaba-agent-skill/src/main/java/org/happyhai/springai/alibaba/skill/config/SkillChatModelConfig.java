package org.happyhai.springai.alibaba.skill.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.skills.SkillsAgentHook;
import com.alibaba.cloud.ai.graph.skills.registry.classpath.ClasspathSkillRegistry;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Skill 模块 ChatModel 和 Agent 配置类
 * 提供 DashScope ChatModel 和 ReactAgent Bean
 */
@Configuration
public class SkillChatModelConfig {

    @Value("${spring.ai.dashscope.api-key:}")
    private String apiKey;

    @Value("${spring.ai.dashscope.chat.model:qwen-plus}")
    private String model;

    @Value("${spring.ai.dashscope.chat.temperature:0.7}")
    private Double temperature;

    @Value("${agent.react.name}")
    private String agentName;

    @Value("${agent.react.enable}")
    private boolean enableLogging;

    @Value("${agent.react.classpath-path}")
    private String classpathPath;

    @Bean
    public ChatModel chatModel() {
        return DashScopeChatModel.builder()
                .dashScopeApi(DashScopeApi.builder()
                        .apiKey(apiKey)
                        .build())
                .defaultOptions(DashScopeChatOptions.builder()
                        .model(model)
                        .temperature(temperature)
                        .build())
                .build();
    }

    /**
     * 技能注册中心，加载、管理所有技能
     *
     * @return 从项目类路径（resources）加载技能
     */
    @Bean
    public ClasspathSkillRegistry skillRegistry() {
        return ClasspathSkillRegistry.builder()
                .classpathPath(classpathPath)
                .build();
    }

    /**
     * 技能钩子，注入技能上下文
     *
     * @param skillRegistry 技能注册中心
     * @return 技能钩子
     */
    @Bean
    public SkillsAgentHook skillsAgentHook(ClasspathSkillRegistry skillRegistry) {
        return SkillsAgentHook.builder()
                .skillRegistry(skillRegistry)
                .build();
    }

    /**
     * 构造智能体
     *
     * @param chatModel 语言模型
     * @param skillsAgentHook 技能钩子
     * @return 智能体
     */
    @Bean
    public ReactAgent reactAgent(ChatModel chatModel, SkillsAgentHook skillsAgentHook) {
        return ReactAgent.builder()
                .name(agentName)
                .model(chatModel)
                .hooks(List.of(skillsAgentHook))
                .enableLogging(enableLogging)
                .build();
    }
}