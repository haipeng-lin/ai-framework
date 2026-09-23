package org.happyhai.agentscope.agent.config;

import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

@Configuration
public class AgentConfig {

    @Bean(destroyMethod = "close")
    public HarnessAgent noteTakerAgent(DashScopeChatModel chatModel) {
        return HarnessAgent.builder()
                .name("note-taker")
                .sysPrompt("你是一位心理情绪小助手")
                .model(chatModel)
                .workspace(Paths.get(".agentscope/workspace"))
                .compaction(CompactionConfig.builder()
                        .triggerMessages(30)
                        .keepMessages(10)
                        .build())
                .build();
    }

}
