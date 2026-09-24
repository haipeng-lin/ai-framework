package org.happyhai.agentscope.classify;

import org.happyhai.agentscope.classify.intent.IntentResult;
import org.happyhai.agentscope.classify.intent.IntentRouter;
import org.happyhai.agentscope.classify.intent.IntentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 纯 Java 的路由逻辑单测，不依赖 Spring / LLM。 */
class IntentRouterTest {

    private final IntentRouter router = new IntentRouter();

    @Test
    void routesChitChat() {
        IntentResult r = IntentResult.simple(IntentType.CHIT_CHAT, 0.9, java.util.Map.of(), "hi");
        IntentRouter.RouteDecision d = router.route(r, 2);
        assertEquals(IntentRouter.CHAT_AGENT, d.agentBean());
        assertFalse(d.enterPlanMode());
    }

    @Test
    void routesKnowledgeQa() {
        IntentResult r = IntentResult.simple(IntentType.KNOWLEDGE_QA, 0.9, java.util.Map.of(), "什么是草缸");
        IntentRouter.RouteDecision d = router.route(r, 2);
        assertEquals(IntentRouter.KNOWLEDGE_AGENT, d.agentBean());
    }

    @Test
    void routesExplicitControl() {
        IntentResult r = IntentResult.simple(IntentType.EXPLICIT_CONTROL, 0.9,
                java.util.Map.of("channel", "main", "val", "60"), "主灯亮度60");
        IntentRouter.RouteDecision d = router.route(r, 2);
        assertEquals(IntentRouter.EXPLICIT_CONTROL_AGENT, d.agentBean());
    }

    @Test
    void fuzzyControlSimpleDoesNotEnterPlanMode() {
        IntentResult r = new IntentResult(IntentType.FUZZY_CONTROL, 0.9,
                java.util.Map.of("direction", "brighter"), "调亮一点", 1);
        IntentRouter.RouteDecision d = router.route(r, 2);
        assertEquals(IntentRouter.FUZZY_CONTROL_AGENT, d.agentBean());
        assertFalse(d.enterPlanMode());
    }

    @Test
    void fuzzyControlComplexEntersPlanMode() {
        IntentResult r = new IntentResult(IntentType.FUZZY_CONTROL, 0.9,
                java.util.Map.of("scene", "plant"), "弄一个适合红蝴蝶的草缸灯光方案", 4);
        IntentRouter.RouteDecision d = router.route(r, 2);
        assertEquals(IntentRouter.FUZZY_CONTROL_AGENT, d.agentBean());
        assertTrue(d.enterPlanMode());
    }

    @Test
    void routesStatusQuery() {
        IntentResult r = IntentResult.simple(IntentType.STATUS_QUERY, 0.9, java.util.Map.of(), "现在灯什么模式");
        IntentRouter.RouteDecision d = router.route(r, 2);
        assertEquals(IntentRouter.STATUS_QUERY_AGENT, d.agentBean());
    }

    @Test
    void nullIntentFallsBackToChitChat() {
        IntentRouter.RouteDecision d = router.route(null, 2);
        assertEquals(IntentRouter.CHAT_AGENT, d.agentBean());
    }
}
