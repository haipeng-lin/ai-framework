package org.happyhai.agentscope.classify.domain;

import java.time.Instant;
import java.util.Map;

/** 设备控制指令的执行结果。 */
public record CommandResult(
        String deviceId,
        String commandId,
        boolean success,
        Map<String, Object> appliedParams,
        String message,
        Instant executedAt
) {}
