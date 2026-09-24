package org.happyhai.agentscope.classify.domain;

/** 单通道实时状态：亮度（0-100）与色温（K）。 */
public record ChannelState(
        String name,
        int brightness,
        int colorTemperature
) {}
