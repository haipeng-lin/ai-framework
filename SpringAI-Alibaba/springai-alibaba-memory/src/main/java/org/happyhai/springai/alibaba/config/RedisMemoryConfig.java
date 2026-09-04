package org.happyhai.springai.alibaba.config;

import com.alibaba.cloud.ai.memory.redis.BaseRedisChatMemoryRepository;
import com.alibaba.cloud.ai.memory.redis.JedisRedisChatMemoryRepository;
import com.alibaba.cloud.ai.memory.redis.RedissonRedisChatMemoryRepository;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
 
/**
 * Redis 持久化记忆仓库配置类
 */
@Configuration
public class RedisMemoryConfig {
 
    @Value("${spring.data.redis.host}")
    private String redisHost;
 
    @Value("${spring.data.redis.port}")
    private int redisPort;
 
    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.database}")
    private int database;

    @Bean
    public ChatMemoryRepository redisChatMemoryRepository() {
        return RedissonRedisChatMemoryRepository.builder()
                .host(redisHost)
                .port(redisPort)
                .password(redisPassword)
                .database(database)
                .build();
    }
}