package org.happyhai.springai.alibaba.light.agent.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.springframework.ai.tool.StaticToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AgentConfig {

    /**
     * 设备控制 Agent（节点 3 模糊控制 / 节点 4 明确控制）
     */
    @Bean
    public ReactAgent controlReactAgent(
            @Qualifier("dashScopeChatModel") DashScopeChatModel chatModel,
            @Qualifier("controlTools") List<ToolCallback> controlTools) {
        ToolCallback[] array = controlTools.toArray(new ToolCallback[0]);
        return ReactAgent.builder()
                .name("light_control_agent")
                .model(chatModel)
                .toolCallbackProviders(new StaticToolCallbackProvider(array))
                .enableLogging(true)
                .build();
    }

    /**
     * 查询 Agent（节点 5 故障排查 / 节点 6 状态查询）
     */
    @Bean
    public ReactAgent queryReactAgent(
            @Qualifier("dashScopeChatModel") DashScopeChatModel chatModel,
            @Qualifier("queryTools") List<ToolCallback> queryTools) {
        ToolCallback[] array = queryTools.toArray(new ToolCallback[0]);
        return ReactAgent.builder()
                .name("light_query_agent")
                .model(chatModel)
                .toolCallbackProviders(new StaticToolCallbackProvider(array))
                .enableLogging(true)
                .build();
    }
}
