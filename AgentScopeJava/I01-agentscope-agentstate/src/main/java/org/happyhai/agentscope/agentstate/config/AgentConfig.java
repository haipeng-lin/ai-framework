package org.happyhai.agentscope.agentstate.config;

import io.agentscope.core.state.AgentStateStore;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.redis.state.RedisAgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

    @Bean
    public AgentStateStore agentStateStore() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://localhost:6379").setDatabase(11).setPassword("123456");
        RedissonClient redisson = Redisson.create(config);

        return RedisAgentStateStore.builder()
                .redissonClient(redisson)
                .build();
    }

    @Bean
    public HarnessAgent noteTakerAgent(DashScopeChatModel chatModel, AgentStateStore stateStore) {
        return HarnessAgent.builder()
                .name("note-taker")
                .sysPrompt("你是一位心理情绪小助手")
                .model(chatModel)
                .stateStore(stateStore)
                .compaction(CompactionConfig.builder()
                        .triggerMessages(30)
                        .keepMessages(10)
                        .build())
                .build();
    }


}
