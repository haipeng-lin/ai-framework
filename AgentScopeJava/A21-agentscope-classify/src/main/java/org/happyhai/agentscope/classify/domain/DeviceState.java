package org.happyhai.agentscope.classify.domain;

import java.time.Instant;
import java.util.Map;

/**
 * 设备实时状态快照。
 *
 * <p>由 {@link org.happyhai.agentscope.classify.tool.SyncDeviceStateTool}
 * 从模拟设备后端拉取，是模糊控制 / 状态查询等场景的"前置事实"。
 */
public record DeviceState(
        String deviceId,
        Map<String, ChannelState> channels,
        String currentMode,
        double temperatureCelsius,
        Instant lastUpdated
) {}
