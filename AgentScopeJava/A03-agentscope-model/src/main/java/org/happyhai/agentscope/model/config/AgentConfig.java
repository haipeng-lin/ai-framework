package org.happyhai.agentscope.model.config;

import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

@Configuration
public class AgentConfig {

    @Bean(destroyMethod = "close")
    public HarnessAgent openAiAgent(DashScopeChatModel chatModel) {
        return HarnessAgent.builder()
                .name("openai-agent")
                .sysPrompt("你是一名小助手")
                .model(chatModel)
                .workspace(Paths.get(".agentscope/workspace"))
                .compaction(CompactionConfig.builder()
                        .triggerMessages(30)
                        .keepMessages(10)
                        .build())
                .build();
    }

    @Bean(destroyMethod = "close")
    public HarnessAgent dashScopeAgent(DashScopeChatModel chatModel) {
        return HarnessAgent.builder()
                .name("dashscope-agent")
                .sysPrompt("你是一名小助手")
                .model(chatModel)
                .workspace(Paths.get(".agentscope/workspace"))
                .compaction(CompactionConfig.builder()
                        .triggerMessages(30)
                        .keepMessages(10)
                        .build())
                .build();
    }

}
