package org.happyhai.agentscope.classify.domain;

/**
 * 一次参数兜底记录。
 *
 * <p>例如：用户说"亮度调到 150%"，但设备只支持 0-100，
 * 则会生成一条 {@code (channel="main", original=150, corrected=100, reason="...")} 记录。
 */
public record Correction(
        String channel,
        Object original,
        Object corrected,
        String reason
) {}
