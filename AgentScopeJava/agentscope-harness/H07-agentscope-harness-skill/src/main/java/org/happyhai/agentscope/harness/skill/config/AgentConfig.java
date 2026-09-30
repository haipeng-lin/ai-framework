package org.happyhai.agentscope.harness.skill.config;

import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;

/**
 * 装配一个 HarnessAgent：
 * <ul>
 *   <li>不再显式注册 skill 仓库；bmi-calculator 这个 skill 直接放在
 *       {@code <module>/.agentscope/workspace/skills/bmi-calculator/}，由 harness
 *       从 workspace 根下的 {@code skills/} 子目录自动加载。</li>
 *   <li>system prompt 提示用户在报身高体重时让 agent 走 bmi-calculator 这个 skill。</li>
 *   <li>workspace 目录默认落在当前 Maven 模块根下的 {@code .agentscope/workspace}：
 *       以 AgentConfig.class 的位置为锚点往父目录找 pom.xml；找不到就回退到
 *       {@code user.dir/.agentscope/workspace}；想换位置可以用
 *       {@code agentscope.workspace-dir} 显式覆盖。</li>
 * </ul>
 */
@Configuration
public class AgentConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentConfig.class);

    /**
     * 显式覆盖 workspace 目录。留空就走 {@link #resolveModuleWorkspace()} 的自动探测。
     */
    @Value("${agentscope.workspace-dir:}")
    private String workspaceDirOverride;

    @Bean(destroyMethod = "close")
    public HarnessAgent skillDrivenAgent(DashScopeChatModel chatModel) {
        Path workspace = resolveWorkspacePath();
        log.info("[h07:skill] workspace 目录 = {}", workspace.toAbsolutePath());
        return HarnessAgent.builder()
                .name("skill-driven-assistant")
                .sysPrompt("你是一名健康助理。"
                        + "当用户输入身高和体重时，请使用 bmi-calculator 这个 skill："
                        + "先调用 load_skill_through_path 加载 SKILL.md 了解流程，"
                        + "再读取 references/bmi-classification.md 拿到分类阈值，"
                        + "最后按 SKILL.md 的步骤计算 BMI 并给出健康评估。")
                .model(chatModel)
                .workspace(workspace)
                .compaction(CompactionConfig.builder()
                        .triggerMessages(30)
                        .keepMessages(10)
                        .build())
                .build();
    }

    private Path resolveWorkspacePath() {
        if (workspaceDirOverride != null && !workspaceDirOverride.isBlank()) {
            Path override = Path.of(workspaceDirOverride);
            log.info("[h07:skill] 使用 agentscope.workspace-dir 指定的 workspace：{}",
                    override.toAbsolutePath());
            return override;
        }
        Path auto = resolveModuleWorkspace();
        if (auto != null) {
            return auto;
        }
        Path fallback = Path.of(System.getProperty("user.dir"), ".agentscope", "workspace");
        log.warn("[h07:skill] 无法从 AgentConfig.class 反推模块根，回退到 user.dir 下的 .agentscope/workspace：{}",
                fallback.toAbsolutePath());
        return fallback;
    }

    /**
     * 以 AgentConfig.class 自身的位置为锚点往父目录找 pom.xml，定位当前 Maven 模块根。
     * 仅在 classes 已展开（开发/测试期）时可用；打包成 jar 后 code source 是 jar: 协议，会返回 null。
     */
    private static Path resolveModuleWorkspace() {
        CodeSource source = AgentConfig.class.getProtectionDomain().getCodeSource();
        if (source == null) {
            return null;
        }
        URL location = source.getLocation();
        if (location == null || !"file".equals(location.getProtocol())) {
            return null;
        }
        try {
            Path classFile = Path.of(location.toURI());
            Path moduleRoot = findModuleRoot(classFile);
            if (moduleRoot == null) {
                return null;
            }
            return moduleRoot.resolve(".agentscope/workspace");
        } catch (Exception e) {
            log.warn("[h07:skill] 反推模块根失败：{}", e.toString());
            return null;
        }
    }

    private static Path findModuleRoot(Path start) {
        Path current = start.toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }
}
