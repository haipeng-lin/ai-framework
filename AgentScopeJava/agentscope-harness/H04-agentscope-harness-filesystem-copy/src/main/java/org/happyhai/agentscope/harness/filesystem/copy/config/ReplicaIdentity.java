package org.happyhai.agentscope.harness.filesystem.copy.config;

/**
 * 这个 replica 的身份信息，由 yml 配置 + bean 装配阶段注入。
 * controller 拿到它就能在响应里打"我是哪一份"。
 */
public record ReplicaIdentity(
        String replicaId,
        String agentName,
        String workspaceDir) {
}
