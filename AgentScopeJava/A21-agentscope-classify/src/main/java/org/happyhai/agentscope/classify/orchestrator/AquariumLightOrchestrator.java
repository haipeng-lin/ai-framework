package org.happyhai.agentscope.classify.orchestrator;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.harness.agent.HarnessAgent;
import org.happyhai.agentscope.classify.intent.IntentClassifier;
import org.happyhai.agentscope.classify.intent.IntentResult;
import org.happyhai.agentscope.classify.intent.IntentRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 主调度器（设计稿第 5 节）。
 *
 * <p>工作流：
 * <ol>
 *   <li>调用 {@link IntentClassifier} 得到 IntentResult（含复杂度评分）。</li>
 *   <li>{@link IntentRouter} 按 Java switch 做确定性路由。</li>
 *   <li>如果 FUZZY_CONTROL 且超过 plan-mode 阈值，调用 {@link HarnessAgent#enterPlanMode(RuntimeContext)}。</li>
 *   <li>委派到对应 {@link HarnessAgent} bean 并返回其响应。</li>
 * </ol>
 */
@Service
public class AquariumLightOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AquariumLightOrchestrator.class);

    private final IntentClassifier classifier;
    private final IntentRouter router;
    private final HarnessAgent chatAgent;
    private final HarnessAgent knowledgeAgent;
    private final HarnessAgent explicitControlAgent;
    private final HarnessAgent fuzzyControlAgent;
    private final HarnessAgent statusQueryAgent;
    private final HarnessAgent orchestratorAgent;
    private final int planModeThreshold;

    public AquariumLightOrchestrator(IntentClassifier classifier,
                                     IntentRouter router,
                                     HarnessAgent chatAgent,
                                     HarnessAgent knowledgeAgent,
                                     HarnessAgent explicitControlAgent,
                                     HarnessAgent fuzzyControlAgent,
                                     HarnessAgent statusQueryAgent,
                                     HarnessAgent orchestratorAgent,
                                     @Value("${classify.fuzzy.plan-mode-threshold:2}") int planModeThreshold) {
        this.classifier = classifier;
        this.router = router;
        this.chatAgent = chatAgent;
        this.knowledgeAgent = knowledgeAgent;
        this.explicitControlAgent = explicitControlAgent;
        this.fuzzyControlAgent = fuzzyControlAgent;
        this.statusQueryAgent = statusQueryAgent;
        this.orchestratorAgent = orchestratorAgent;
        this.planModeThreshold = planModeThreshold;
    }

    public record OrchestrationOutcome(
            IntentResult intent,
            IntentRouter.RouteDecision route,
            Msg reply,
            Map<String, Object> trace
    ) {}

    /**
     * 同步版调度：完整跑一遍分类 + 路由 + 委派。
     */
    public OrchestrationOutcome handle(String userPrompt, RuntimeContext ctx) {
        IntentResult intent = classifier.classify(userPrompt);
        IntentRouter.RouteDecision decision = router.route(intent, planModeThreshold);
        HarnessAgent target = pickAgent(decision.agentBean());
        if (decision.enterPlanMode()) {
            log.info("Entering Plan Mode: sessionId={}, reason={}", ctx.getSessionId(), decision.reason());
            target.enterPlanMode(ctx);
        }
        Msg reply = target.call(buildPrompt(userPrompt, intent), ctx).block();
        Map<String, Object> trace = new HashMap<>();
        trace.put("orchestratorHint", orchestratorAgent.getName());
        trace.put("targetAgent", target.getName());
        trace.put("planMode", decision.enterPlanMode());
        return new OrchestrationOutcome(intent, decision, reply, trace);
    }

    /** 委派前先暴露分类结果，便于 SSE 流式响应在第一帧就把意图回给前端。 */
    public IntentResult classifyOnly(String userPrompt) {
        return classifier.classify(userPrompt);
    }

    /** 给流式入口使用：同步算出路由决策，方便在 SSE 第一帧就把结果推给前端。 */
    public IntentRouter.RouteDecision routeFor(String userPrompt) {
        return router.route(classifier.classify(userPrompt), planModeThreshold);
    }

    /**
     * 流式调度：分类仍同步完成（快通道），委派阶段返回 Agent 的事件流。
     *
     * <p>调用方拿到的不再是 {@code Mono<Msg>}，而是 {@code Flux<AgentEvent>}，便于
     * 在 SSE / WebSocket 中按帧推送文本 / thinking / tool_call 等增量。
     */
    public reactor.core.publisher.Flux<AgentEvent> handleStream(String userPrompt,
                                                                  RuntimeContext ctx) {
        IntentResult intent = classifier.classify(userPrompt);
        IntentRouter.RouteDecision decision = router.route(intent, planModeThreshold);
        HarnessAgent target = pickAgent(decision.agentBean());
        if (decision.enterPlanMode()) {
            target.enterPlanMode(ctx);
        }
        return target.streamEvents(buildPrompt(userPrompt, intent), ctx);
    }

    private HarnessAgent pickAgent(String bean) {
        if (bean == null) {
            return chatAgent;
        }
        return switch (bean) {
            case IntentRouter.CHAT_AGENT -> chatAgent;
            case IntentRouter.KNOWLEDGE_AGENT -> knowledgeAgent;
            case IntentRouter.EXPLICIT_CONTROL_AGENT -> explicitControlAgent;
            case IntentRouter.FUZZY_CONTROL_AGENT -> fuzzyControlAgent;
            case IntentRouter.STATUS_QUERY_AGENT -> statusQueryAgent;
            default -> chatAgent;
        };
    }

    private static String buildPrompt(String raw, IntentResult intent) {
        if (intent.slots() == null || intent.slots().isEmpty()) {
            return raw;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("用户原始输入：").append(raw).append('\n');
        sb.append("已抽取的参数：").append(intent.slots()).append('\n');
        sb.append("请按你的指令处理以上需求。");
        return sb.toString();
    }
}
