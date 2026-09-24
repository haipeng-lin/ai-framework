package org.happyhai.agentscope.middleware.config;

import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;
import java.util.List;

@Configuration
public class AgentConfig {

    @Bean(destroyMethod = "close")
    public HarnessAgent middlewareAgent(DashScopeChatModel chatModel,
                                         Toolkit toolkit,
                                         List<MiddlewareBase> installedMiddlewares) {
        return HarnessAgent.builder()
                .name("middleware-assistant")
                .sysPrompt("你是一名小助手。如果用户问到时间，使用 get_current_time 工具；"
                        + "如果用户让你复述一句话，使用 echo 工具。")
                .model(chatModel)
                .toolkit(toolkit)
                .middlewares(installedMiddlewares)
                .workspace(Paths.get(".agentscope/workspace"))
                .compaction(CompactionConfig.builder()
                        .triggerMessages(30)
                        .keepMessages(10)
                        .build())
                .build();
    }

}
