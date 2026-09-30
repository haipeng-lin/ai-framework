package org.happyhai.agentscope.harness.filesystem.config;

import io.agentscope.core.model.Model;
import io.agentscope.extensions.redis.RedisDistributedStore;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.spec.RemoteFilesystemSpec;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;

import java.nio.file.Path;

/**
 * 装配一个走"共享存储模式"的 HarnessAgent：
 * <ul>
 *   <li>把 {@link RedisProperties} 拼出的 redis:// URI 包成 {@link JedisPooled}，
 *       再用 {@link RedisDistributedStore#fromJedis} 拿到统一的
 *       {@link DistributedStore}（同时覆盖 stateStore 和 baseStore）。</li>
 *   <li>builder 挂上 {@code .distributedStore(store)} 与 {@code new RemoteFilesystemSpec()}，
 *       baseStore 会由 {@code distributedStore} 自动注入，调用方不需要再传。</li>
 *   <li>{@link IsolationScope#USER}：同一 userId 跨会话共享工作区文件；
 *       未传 userId 时降级按 SESSION 隔离。</li>
 *   <li>workspace 目录由 {@code application.yml} 里的 {@code agentscope.workspace-dir} 直接给，
 *       默认是相对路径 {@code .agentscope/workspace}，相对当前工作目录解析。</li>
 * </ul>
 */
@Configuration
public class AgentConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentConfig.class);

    @Value("${agentscope.workspace-dir}")
    private String workspaceDir;

    @Value("${agentscope.agent-name}")
    private String agentName;

    @Bean
    public DistributedStore redisDistributedStore(RedisDistributedProperties props) {
        // 连接池配置
        ConnectionPoolConfig poolConfig = new ConnectionPoolConfig();
        poolConfig.setMaxTotal(props.getMaxTotal());

        // 客户端配置：密码、库索引、超时
        DefaultJedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
                .password(props.getPassword())
                .database(props.getDatabase())
                .connectionTimeoutMillis(props.getTimeout())
                .socketTimeoutMillis(props.getTimeout())
                .build();

        JedisPooled jedis = new JedisPooled(
                poolConfig,
                new HostAndPort(props.getHost(), props.getPort()),
                clientConfig);

        return RedisDistributedStore.fromJedis(jedis);
    }

    /**
     * 跑共享存储模式的 HarnessAgent：把 RemoteFilesystemSpec 挂上去，
     * agent 的工作区文件（MEMORY.md / memory/ / sessions/ 等）就会按内置路由表
     * 自动写到 Redis，多副本共享同一份。
     */
    @Bean()
    public HarnessAgent sharedStoreAgent(Model model, DistributedStore distributedStore) {
        Path workspace = Path.of(workspaceDir);
        return HarnessAgent.builder()
                .name(agentName)
                .sysPrompt("你是一个笔记助手。"
                        + "当用户告诉你任何偏好、事实、约定（例如喜欢的颜色、所在城市、生日等），"
                        + "请把它们写入工作区的 MEMORY.md 或 memory/ 目录下，"
                        + "这样下次启动还能回忆起来。")
                .model(model)
                .workspace(workspace)
                .distributedStore(distributedStore)
                .filesystem(new RemoteFilesystemSpec()
                        .isolationScope(IsolationScope.USER)
                        .anonymousUserId("anonymous"))
                .compaction(CompactionConfig.builder()
                        .triggerMessages(30)
                        .keepMessages(10)
                        .build())
                .build();
    }
}
