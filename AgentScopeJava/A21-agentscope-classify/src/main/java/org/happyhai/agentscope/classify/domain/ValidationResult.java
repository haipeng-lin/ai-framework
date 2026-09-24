package org.happyhai.agentscope.classify.domain;

import java.util.List;

/**
 * 参数校验结果。
 *
 * <p>如果校验通过，{@link #corrections} 为空列表；如果触发兜底，
 * 每条 {@link Correction} 记录了"原始值 → 修正值"以及原因。
 */
public record ValidationResult(
        Status status,
        java.util.Map<String, Object> correctedParams,
        List<Correction> corrections
) {
    public enum Status {
        /** 校验通过，未做任何改动。 */
        OK,
        /** 校验通过但做了兜底（值被夹到合法范围）。 */
        CORRECTED,
        /** 校验失败，安全规则被违反。 */
        REJECTED
    }
}
