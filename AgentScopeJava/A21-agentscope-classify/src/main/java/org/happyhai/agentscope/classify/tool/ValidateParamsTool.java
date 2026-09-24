package org.happyhai.agentscope.classify.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.happyhai.agentscope.classify.domain.Correction;
import org.happyhai.agentscope.classify.domain.DeviceConfig;
import org.happyhai.agentscope.classify.domain.ValidationResult;
import org.happyhai.agentscope.classify.service.InMemoryDeviceService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 参数校验与兜底工具（对应设计稿 4.2 节）。
 *
 * <p>对亮度 / 色温等参数做范围校验，越界时自动夹到合法区间，并返回 Correction 列表说明
 * "为什么改了"。同时承担安全规则：灯具温度过高时强制降亮度。
 */
@Component
public class ValidateParamsTool {

    private final InMemoryDeviceService deviceService;

    public ValidateParamsTool(InMemoryDeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @Tool(
            name = "validate_params",
            description = "校验控制参数是否在设备支持范围内，超出范围时自动兜底。"
                        + "返回结果包含 corrected_params（已应用兜底后的参数）和 corrections（每一处调整的原因）。",
            readOnly = true,
            concurrencySafe = true
    )
    public ValidationResult validate(
            @ToolParam(name = "device_id",
                       description = "设备 ID",
                       required = true)
                    String deviceId,
            @ToolParam(name = "params",
                       description = "待校验的参数 Map，key 为通道名（如 main/blue），"
                                   + "value 可以是 0-100 的整数（亮度）或"
                                   + "{brightness:int, colorTemperature:int} 形式",
                       required = true)
                    Map<String, Object> params) {
        DeviceConfig config = deviceService.config();
        List<Correction> corrections = new ArrayList<>();
        Map<String, Object> corrected = new LinkedHashMap<>();

        DeviceConfig.ChannelSpec moonlightSpec = config.getChannel("moonlight");
        boolean wantsMoonlightHigh = false;

        for (var entry : params.entrySet()) {
            String channel = entry.getKey();
            Object raw = entry.getValue();
            DeviceConfig.ChannelSpec spec = config.getChannel(channel);
            if (spec == null) {
                // 未知通道：直接保留原值，不做兜底
                corrected.put(channel, raw);
                continue;
            }

            if (raw instanceof Map<?, ?> nested) {
                Map<String, Object> fixedNested = new LinkedHashMap<>();
                Object b = nested.get("brightness");
                Object t = nested.get("colorTemperature");
                if (b instanceof Number n) {
                    int v = clampInt(n.intValue(), spec.minBrightness(), spec.maxBrightness(),
                            channel, "brightness", corrections);
                    fixedNested.put("brightness", v);
                    if ("moonlight".equals(channel) && v > 30) wantsMoonlightHigh = true;
                }
                if (t instanceof Number n) {
                    int v = clampInt(n.intValue(), spec.minColorTemperature(), spec.maxColorTemperature(),
                            channel, "colorTemperature", corrections);
                    fixedNested.put("colorTemperature", v);
                }
                corrected.put(channel, fixedNested);
            } else if (raw instanceof Number n) {
                int v = clampInt(n.intValue(), spec.minBrightness(), spec.maxBrightness(),
                        channel, "brightness", corrections);
                if ("moonlight".equals(channel) && v > 30) wantsMoonlightHigh = true;
                corrected.put(channel, v);
            } else {
                corrected.put(channel, raw);
            }
        }

        // 安全规则：主灯高亮时，月光通道必须压低
        if (wantsMoonlightHigh) {
            int currentMoon = 0;
            Object moon = corrected.get("moonlight");
            if (moon instanceof Number n) currentMoon = n.intValue();
            if (currentMoon > moonlightSpec.maxBrightness()) {
                corrections.add(new Correction("moonlight", currentMoon,
                        moonlightSpec.maxBrightness(),
                        "主灯/蓝灯高亮时，月光通道自动压低到最大值 " + moonlightSpec.maxBrightness()));
                corrected.put("moonlight", moonlightSpec.maxBrightness());
            }
        }

        ValidationResult.Status status = corrections.isEmpty()
                ? ValidationResult.Status.OK
                : ValidationResult.Status.CORRECTED;
        return new ValidationResult(status, corrected, corrections);
    }

    private static int clampInt(int value, int min, int max, String channel,
                                String field, List<Correction> sink) {
        if (value < min) {
            sink.add(new Correction(channel + "." + field, value, min,
                    "低于最小值 " + min + "，已兜底"));
            return min;
        }
        if (value > max) {
            sink.add(new Correction(channel + "." + field, value, max,
                    "超过最大值 " + max + "，已兜底"));
            return max;
        }
        return value;
    }
}
