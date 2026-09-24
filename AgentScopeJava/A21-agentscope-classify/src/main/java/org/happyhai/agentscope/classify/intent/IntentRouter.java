package org.happyhai.agentscope.classify.intent;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 确定性 Java 路由（设计稿第 5 节）。
 *
 * <p>不调用 LLM；只根据 {@link IntentResult#intent()} 把请求分派到对应的 {@code HarnessAgent} bean 名。
 * 这样整个调度路径可观测、可单测、可被 Spring Security 做权限控制。
 */
@Component
public class IntentRouter {

    public static final String CHAT_AGENT = "chatAgent";
    public static final String KNOWLEDGE_AGENT = "knowledgeAgent";
    public static final String EXPLICIT_CONTROL_AGENT = "explicitControlAgent";
    public static final String FUZZY_CONTROL_AGENT = "fuzzyControlAgent";
    public static final String STATUS_QUERY_AGENT = "statusQueryAgent";

    /** 路由结果：目标 agent + 是否需要进入 Plan Mode。 */
    public record RouteDecision(String agentBean, boolean enterPlanMode, String reason) {
        public RouteDecision(String agentBean) {
            this(agentBean, false, "default");
        }
    }

    /**
     * 把意图分派到具体的 Agent Bean 名。
     *
     * <p>对于 {@link IntentType#FUZZY_CONTROL}，会根据 {@link IntentResult#complexityScore()}
     * 决定是否要进入 Plan Mode（多参数/场景切换 → 启用）。
     */
    public RouteDecision route(IntentResult result, int planModeThreshold) {
        if (result == null || result.intent() == null) {
            return new RouteDecision(CHAT_AGENT, false, "fallback_chit_chat");
        }
        return switch (result.intent()) {
            case CHIT_CHAT -> new RouteDecision(CHAT_AGENT);
            case KNOWLEDGE_QA -> new RouteDecision(KNOWLEDGE_AGENT);
            case EXPLICIT_CONTROL -> new RouteDecision(EXPLICIT_CONTROL_AGENT);
            case FUZZY_CONTROL -> {
                boolean needPlan = result.complexityScore() >= planModeThreshold;
                String reason = "complexity=" + result.complexityScore()
                        + ", threshold=" + planModeThreshold;
                yield new RouteDecision(FUZZY_CONTROL_AGENT, needPlan, reason);
            }
            case STATUS_QUERY -> new RouteDecision(STATUS_QUERY_AGENT);
        };
    }

    /** 调试用：把 RouteDecision 序列化成简单 Map，便于在 controller 直接返回。 */
    public static Map<String, Object> describe(IntentResult result, RouteDecision decision) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("intent", result.intent().name());
        view.put("confidence", result.confidence());
        view.put("slots", result.slots());
        view.put("complexityScore", result.complexityScore());
        view.put("rawUtterance", result.rawUtterance());
        view.put("agentBean", decision.agentBean());
        view.put("enterPlanMode", decision.enterPlanMode());
        view.put("reason", decision.reason());
        return view;
    }
}
