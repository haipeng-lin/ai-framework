package org.happyhai.agentscope.classify.intent;

import java.util.Map;

/**
 * 意图分类输出结构。
 *
 * <p>对应原始设计稿 2.3 节 Slot 抽取结构：
 * <pre>
 *   EXPLICIT_CONTROL -> { channel: "main", brightness: 60, unit: "percent" }
 *   FUZZY_CONTROL    -> { direction: "brighter", degree: "slightly" }
 *   STATUS_QUERY     -> { target: "schedule", channel: "moonlight" }
 * </pre>
 *
 * <p>{@code complexityScore} 仅在 {@link IntentType#FUZZY_CONTROL} 时有意义：
 * 越大代表场景越复杂，越倾向于进入 Plan Mode 让用户先确认方案。
 */
public record IntentResult(
        IntentType intent,
        double confidence,
        Map<String, Object> slots,
        String rawUtterance,
        int complexityScore
) {
    public IntentResult {
        if (slots == null) {
            slots = Map.of();
        }
    }

    /** 简化版构造（用于规则引擎快速通道，不关心复杂度评分）。 */
    public static IntentResult simple(IntentType intent, double confidence,
                                      Map<String, Object> slots, String raw) {
        return new IntentResult(intent, confidence, slots, raw, 0);
    }
}
