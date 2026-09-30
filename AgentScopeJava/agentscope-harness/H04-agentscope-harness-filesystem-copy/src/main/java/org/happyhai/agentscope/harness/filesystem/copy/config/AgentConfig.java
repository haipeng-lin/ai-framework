package org.happyhai.agentscope.harness.filesystem.copy.config;

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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;

import java.nio.file.Path;

/**
 * H04-copy 的装配。跟 H04 的 AgentConfig 几乎一致，但有几个关键点不一样：
 * <ul>
 *   <li>agent.name 必须跟 H04 完全一致 —— USER 命名空间是
 *       {@code agents/<agentId>/users/<userId>/...}，agentId 相同才能让两个副本
 *       把同一 userId 的数据写到同一组 Redis key 上。</li>
 *   <li>Redis URI 必须跟 H04 完全一致（host/port/password/database）。</li>
 *   <li>本地 workspace 路径故意和 H04 不一样，证明它只是只读模板。</li>
 *   <li>每个 replica 自己有一个 {@code replica-id}（来自 yml），用于在响应里
 *       标识"我是哪一份"，便于多副本测试时观察路由。</li>
 * </ul>
 */
@Configuration
public class AgentConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentConfig.class);

    @Value("${agentscope.workspace-dir}")
    private String workspaceDir;

    @Value("${agentscope.agent-name}")
    private String agentName;

    @Value("${agentscope.replica-id:replica-unknown}")
    private String replicaId;

    @Bean
    public DistributedStore redisDistributedStore(RedisDistributedProperties props) {
        // 连接池配置
        ConnectionPoolConfig poolConfig = new ConnectionPoolConfig();
        poolConfig.setMaxTotal(props.getMaxTotal());

        // 客户端配置：密码、库索引、超时
        DefaultJedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
                .password(props.getPassword())          // 无密码传 null 即可
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
    @Bean
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

    /**
     * 暴露 replica-id 给 controller，用于在每个响应里打"我是哪一份"的标记。
     */
    @Bean
    public ReplicaIdentity replicaIdentity() {
        return new ReplicaIdentity(replicaId, agentName, workspaceDir);
    }
}
