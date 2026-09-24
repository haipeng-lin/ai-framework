package org.happyhai.agentscope.classify.domain;

import java.util.Map;

/**
 * 设备能力配置：每个通道支持的范围。
 *
 * <p>供 {@link org.happyhai.agentscope.classify.tool.ValidateParamsTool} 做参数校验与兜底。
 */
public record DeviceConfig(
        Map<String, ChannelSpec> channels
) {
    public ChannelSpec getChannel(String name) {
        return channels.get(name);
    }

    public record ChannelSpec(
            String name,
            int minBrightness,
            int maxBrightness,
            int minColorTemperature,
            int maxColorTemperature
    ) {
        public boolean supportsBrightness() {
            return maxBrightness > minBrightness;
        }
    }
}
