package org.happyhai.agentscope.classify;

import io.agentscope.core.model.Model;
import org.happyhai.agentscope.classify.intent.IntentClassifier;
import org.happyhai.agentscope.classify.intent.IntentResult;
import org.happyhai.agentscope.classify.intent.IntentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 只测规则引擎分支：用 Mockito 模拟 Model，LLM 路径不会被触发。 */
class IntentClassifierRuleTest {

    private IntentClassifier classifier;

    @BeforeEach
    void setup() {
        Model model = Mockito.mock(Model.class, Mockito.RETURNS_DEEP_STUBS);
        classifier = new IntentClassifier(model, true);
    }

    @Test
    void explicitControlPattern() {
        // "主灯亮度60" 同时命中 channel + val 两个 named group
        IntentResult r = classifier.classify("主灯亮度60");
        assertEquals(IntentType.EXPLICIT_CONTROL, r.intent());
        assertEquals("主灯", r.slots().get("channel"));
        assertEquals("60", r.slots().get("val"));
    }

    @Test
    void explicitControlPatternWithoutChannel() {
        // "调到60%" 仍能命中 EXPLICIT_CONTROL，channel slot 为空但 val 被抽取
        IntentResult r = classifier.classify("调到60%");
        assertEquals(IntentType.EXPLICIT_CONTROL, r.intent());
        assertEquals("60", r.slots().get("val"));
    }

    @Test
    void fuzzyControlPatternWithComplexity() {
        IntentResult r = classifier.classify("调亮一点，主灯和色温都升一些");
        assertEquals(IntentType.FUZZY_CONTROL, r.intent());
        assertTrue(r.complexityScore() >= 1);
    }

    @Test
    void fuzzyControlPlanModeTrigger() {
        IntentResult r = classifier.classify("帮我弄一个适合红蝴蝶水草的灯光方案");
        assertEquals(IntentType.FUZZY_CONTROL, r.intent());
        assertTrue(r.complexityScore() >= 2,
                "complexity should be >= 2 to trigger Plan Mode, got " + r.complexityScore());
    }

    @Test
    void statusQueryPattern() {
        IntentResult r = classifier.classify("现在灯在什么模式？");
        assertEquals(IntentType.STATUS_QUERY, r.intent());
    }

    @Test
    void knowledgePattern() {
        IntentResult r = classifier.classify("草缸和珊瑚缸有什么区别？");
        assertEquals(IntentType.KNOWLEDGE_QA, r.intent());
    }

    @Test
    void emptyInputFallsBackToChitChat() {
        IntentResult r = classifier.classify("");
        assertEquals(IntentType.CHIT_CHAT, r.intent());
    }
}
