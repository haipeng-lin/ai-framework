package org.happyhai.agentscope.mcp.client.config;

import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

@Configuration
public class AgentConfig {

    @Bean(destroyMethod = "close")
    public HarnessAgent timeAssistantAgent(DashScopeChatModel chatModel, Toolkit toolkit) {
        return HarnessAgent.builder()
                .name("mcp-time-assistant")
                .sysPrompt("你是一名帮助用户解疑的小助手")
                .model(chatModel)
                .toolkit(toolkit)
                .workspace(Paths.get(".agentscope/workspace"))
                // AgentScope v2 默认 permission mode 是 ASK，对 MCP 工具会让 agent 卡在 ASKING 状态。
                // 这里 demo 场景是只读时间工具，直接 BYPASS 跳过权限检查。
                // 生产环境按工具分级配置 allow/deny/ask rules。
                .permissionContext(PermissionContextState.builder()
                        .mode(PermissionMode.BYPASS)
                        .build())
                .compaction(CompactionConfig.builder()
                        .triggerMessages(30)
                        .keepMessages(10)
                        .build())
                .build();
    }
}
