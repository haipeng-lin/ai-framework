package org.happyhai.agentscope.permission.config;

import io.agentscope.core.state.AgentStateStore;
import io.agentscope.extensions.redis.state.RedisAgentStateStore;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StateStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(StateStoreConfig.class);

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        log.info("[state-store] creating Redisson client -> redis://localhost:6379 db=11");
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://localhost:6379")
                .setDatabase(11)
                .setPassword("123456");
        RedissonClient client = Redisson.create(config);
        log.info("[state-store] Redisson client ready: id={}", client.getId());
        return client;
    }

    @Bean(destroyMethod = "close")
    public AgentStateStore agentStateStore(RedissonClient redissonClient) {
        log.info("[state-store] creating RedisAgentStateStore");
        AgentStateStore store = RedisAgentStateStore.builder()
                .redissonClient(redissonClient)
                .build();
        log.info("[state-store] RedisAgentStateStore ready: {}", store.getClass().getSimpleName());
        return store;
    }

}