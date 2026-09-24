package org.happyhai.agentscope.classify.agent;

import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import org.happyhai.agentscope.classify.intent.IntentClassifier;
import org.happyhai.agentscope.classify.tool.AquariumKnowledgeTool;
import org.happyhai.agentscope.classify.tool.DeviceManualTool;
import org.happyhai.agentscope.classify.tool.QueryDeviceHistoryTool;
import org.happyhai.agentscope.classify.tool.QueryDeviceScheduleTool;
import org.happyhai.agentscope.classify.tool.QueryDeviceStatusTool;
import org.happyhai.agentscope.classify.tool.SendControlCmdTool;
import org.happyhai.agentscope.classify.tool.SyncDeviceStateTool;
import org.happyhai.agentscope.classify.tool.ValidateParamsTool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

/**
 * 五个子 Agent + OrchestratorAgent 的 {@link HarnessAgent} bean 配置。
 *
 * <p>每个 Agent 拥有独立的 {@link Toolkit}（按设计稿 3.1-3.5 节定义各自的工具集）。
 * 共用同一个 {@link Model} bean，workspace 也对齐，方便用同一份会话状态做端到端调试。
 */
@Configuration
public class SubAgentConfigs {

    @Bean(destroyMethod = "close")
    public HarnessAgent chatAgent(Model model) {
        return HarnessAgent.builder()
                .name("aquarium-chat")
                .description("闲聊 / 情感交流 / 引导回水族灯话题")
                .sysPrompt("""
                        你是水族灯智能助手"小灯"。性格温和、专业、偶尔幽默。
                        当用户闲聊、问候或表达情感时，自然回应，适时引导回水族灯相关话题。
                        不要主动推荐控制操作，除非用户明确需要。回复控制在 1-3 句话。
                        """)
                .model(model)
                .workspace(Paths.get(".agentscope/workspace/classify"))
                .compaction(CompactionConfig.builder()
                        .triggerMessages(30)
                        .keepMessages(10)
                        .build())
                .maxIters(20)
                .build();
    }

    @Bean(destroyMethod = "close")
    public HarnessAgent knowledgeAgent(Model model,
                                       AquariumKnowledgeTool knowledge,
                                       DeviceManualTool manual) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(knowledge);
        toolkit.registerTool(manual);
        return HarnessAgent.builder()
                .name("aquarium-knowledge")
                .description("知识问答：养鱼、水草、灯光光谱原理")
                .sysPrompt("""
                        你是水族灯光专家，回答用户关于养鱼、水草、珊瑚、灯光光谱的通用知识。
                        必要时调用 aquarium_knowledge / device_manual 工具检索条目；
                        找不到时坦诚告知，并引导用户提供更具体的关键词。
                        """)
                .model(model)
                .toolkit(toolkit)
                .workspace(Paths.get(".agentscope/workspace/classify"))
                .compaction(CompactionConfig.builder()
                        .triggerMessages(30)
                        .keepMessages(10)
                        .build())
                .maxIters(20)
                .build();
    }

    @Bean(destroyMethod = "close")
    public HarnessAgent explicitControlAgent(Model model,
                                             SyncDeviceStateTool sync,
                                             ValidateParamsTool validate,
                                             SendControlCmdTool send) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(sync);
        toolkit.registerTool(validate);
        toolkit.registerTool(send);
        return HarnessAgent.builder()
                .name("explicit-control")
                .description("明确控制：参数齐全，按顺序 sync → validate → send")
                .sysPrompt("""
                        你是水族灯控制执行器。用户给出的控制参数明确且完整。
                        请严格按以下顺序操作：
                          1. 调用 sync_device_state 获取设备当前状态（避免重复拉取）。
                          2. 调用 validate_params 校验参数合法性；如发生兜底，向用户说明。
                          3. 校验通过后调用 send_control_cmd 执行。
                          4. 执行后用 1-2 句话告诉用户结果。
                        注意安全规则：超过 60% 持续亮度建议延长光照时间，而不是直接拉满。
                        """)
                .model(model)
                .toolkit(toolkit)
                .workspace(Paths.get(".agentscope/workspace/classify"))
                .compaction(CompactionConfig.builder()
                        .triggerMessages(30)
                        .keepMessages(10)
                        .build())
                .maxIters(30)
                .build();
    }

    @Bean(destroyMethod = "close")
    public HarnessAgent fuzzyControlAgent(Model model,
                                          SyncDeviceStateTool sync,
                                          AquariumKnowledgeTool knowledge,
                                          ValidateParamsTool validate,
                                          SendControlCmdTool send) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(sync);
        toolkit.registerTool(knowledge);
        toolkit.registerTool(validate);
        toolkit.registerTool(send);
        return HarnessAgent.builder()
                .name("fuzzy-control")
                .description("模糊控制：理解亮一点等定性描述，推理参数方案")
                .sysPrompt("""
                        你是水族灯智能调光师。用户的控制需求比较模糊，需要你结合当前状态推理参数方案。
                        标准流程：
                          1. 调用 sync_device_state 同步当前状态。
                          2. 若涉及适合草缸或某种水草等场景，先调用 aquarium_knowledge。
                          3. 在回复中明确说明你的推理过程，例如亮一点等于当前基础上 +15%。
                          4. 调用 validate_params 兜底并向用户说明。
                          5. 调用 send_control_cmd 执行。
                        参考规则：
                          - 调亮一点: 当前亮度 +15%，不超过 100%
                          - 调暗一点: 当前亮度 -15%，不低于 0%
                          - 暖一些 / 冷一些: 色温 ±500K
                          - 适合草缸: 主灯 60-85%，红光 20-35%
                          - 月光模式: 主灯 0%，月光通道 5-20%
                        """)
                .model(model)
                .toolkit(toolkit)
                .workspace(Paths.get(".agentscope/workspace/classify"))
                .compaction(CompactionConfig.builder()
                        .triggerMessages(30)
                        .keepMessages(10)
                        .build())
                .maxIters(30)
                // Plan Mode：复杂场景输出方案等待用户确认（设计稿 3.4 节）
                .enablePlanMode(true)
                .planFileDirectory("plans/fuzzy")
                .build();
    }

    @Bean(destroyMethod = "close")
    public HarnessAgent statusQueryAgent(Model model,
                                         SyncDeviceStateTool sync,
                                         QueryDeviceStatusTool queryStatus,
                                         QueryDeviceScheduleTool querySchedule,
                                         QueryDeviceHistoryTool queryHistory) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(sync);
        toolkit.registerTool(queryStatus);
        toolkit.registerTool(querySchedule);
        toolkit.registerTool(queryHistory);
        return HarnessAgent.builder()
                .name("status-query")
                .description("状态查询：当前亮度/色温/定时/历史")
                .sysPrompt("""
                        你是水族灯状态播报员。用户想了解设备当前状态。
                          1. 调用 sync_device_state 获取最新数据。
                          2. 必要时调用 query_device_schedule / query_device_history 补充。
                          3. 用结构化、易读的方式汇报给用户。
                          4. 如果发现异常（如温度过高、亮度满载持续），主动提醒。
                        """)
                .model(model)
                .toolkit(toolkit)
                .workspace(Paths.get(".agentscope/workspace/classify"))
                .compaction(CompactionConfig.builder()
                        .triggerMessages(30)
                        .keepMessages(10)
                        .build())
                .maxIters(20)
                .build();
    }

    /**
     * Orchestrator 顶层 Agent：本身只暴露 classify_intent 一个工具，
 真正的派发由 {@link org.happyhai.agentscope.classify.orchestrator.AquariumLightOrchestrator}
 * 在 Java 侧完成（确定性路由）。
 */
    @Bean(destroyMethod = "close")
    public HarnessAgent orchestratorAgent(Model model, IntentClassifier classifier) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(classifier);
        return HarnessAgent.builder()
                .name("aquarium-orchestrator")
                .description("顶层编排 Agent：识别意图后交由 Java Router 派发")
                .sysPrompt("""
                        你是水族灯智能助手。先调用 classify_intent 工具分析用户意图，
                        把工具返回的 IntentResult 交给底层 Java 路由器派发到对应的子 Agent。
                        不要试图自己回复用户，只需返回 IntentResult 摘要即可。
                        """)
                .model(model)
                .toolkit(toolkit)
                .workspace(Paths.get(".agentscope/workspace/classify"))
                .compaction(CompactionConfig.builder()
                        .triggerMessages(20)
                        .keepMessages(8)
                        .build())
                .maxIters(8)
                .build();
    }
}
