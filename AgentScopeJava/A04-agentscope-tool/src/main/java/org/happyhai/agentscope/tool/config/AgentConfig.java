package org.happyhai.agentscope.tool.config;

import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.core.tool.Toolkit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

@Configuration
public class AgentConfig {

    @Bean(destroyMethod = "close")
    public HarnessAgent toolUsingAgent(DashScopeChatModel chatModel, Toolkit toolkit) {
        return HarnessAgent.builder()
                .name("tool-using-assistant")
                .sysPrompt("你是一名小助手。")
                .model(chatModel)
                .toolkit(toolkit)
                .workspace(Paths.get(".agentscope/workspace"))
                .compaction(CompactionConfig.builder()
                        .triggerMessages(30)
                        .keepMessages(10)
                        .build())
                .build();
    }

}
