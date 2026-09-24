package org.happyhai.agentscope.classify.service;

import org.happyhai.agentscope.classify.domain.ChannelState;
import org.happyhai.agentscope.classify.domain.CommandResult;
import org.happyhai.agentscope.classify.domain.DeviceConfig;
import org.happyhai.agentscope.classify.domain.DeviceState;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版"设备后端"，纯本地模拟，没有真实硬件依赖。
 *
 * <p>在生产场景里这里会替换成 MQTT / HTTP / 设备 SDK 客户端。
 */
@Service
public class InMemoryDeviceService {

    private final DeviceConfig config = new DeviceConfig(Map.of(
            "main",      new DeviceConfig.ChannelSpec("main",      0, 100, 2700, 6500),
            "blue",      new DeviceConfig.ChannelSpec("blue",      0, 100, 6500, 20000),
            "red",       new DeviceConfig.ChannelSpec("red",       0, 100, 2700, 6500),
            "moonlight", new DeviceConfig.ChannelSpec("moonlight", 0, 30,  2700, 3000)
    ));

    private final Map<String, DeviceState> stateByDevice = new ConcurrentHashMap<>();

    public InMemoryDeviceService() {
        // 预置一个默认设备，方便示例直接调用
        stateByDevice.put("light-001", new DeviceState(
                "light-001",
                new LinkedHashMap<>(Map.of(
                        "main",      new ChannelState("main",      40, 5500),
                        "blue",      new ChannelState("blue",      20, 9000),
                        "red",       new ChannelState("red",        0, 3500),
                        "moonlight", new ChannelState("moonlight",  0, 2700)
                )),
                "manual",
                32.5,
                Instant.now()
        ));
    }

    public DeviceConfig config() {
        return config;
    }

    public List<String> deviceIds() {
        return List.copyOf(stateByDevice.keySet());
    }

    /** 拉取最新状态（模拟 IO 开销忽略不计）。 */
    public DeviceState fetchState(String deviceId) {
        DeviceState current = stateByDevice.get(deviceId);
        if (current == null) {
            throw new IllegalArgumentException("Unknown deviceId=" + deviceId);
        }
        // 模拟"刚拉取过"的最新时间戳
        DeviceState fresh = new DeviceState(
                current.deviceId(),
                current.channels(),
                current.currentMode(),
                current.temperatureCelsius(),
                Instant.now()
        );
        stateByDevice.put(deviceId, fresh);
        return fresh;
    }

    /** 记录一次控制指令；模拟下发的副作用是写入 state。 */
    public CommandResult applyCommand(String deviceId, Map<String, Object> params) {
        DeviceState current = stateByDevice.get(deviceId);
        if (current == null) {
            throw new IllegalArgumentException("Unknown deviceId=" + deviceId);
        }
        Map<String, ChannelState> nextChannels = new LinkedHashMap<>(current.channels());
        for (var entry : params.entrySet()) {
            String channel = entry.getKey();
            ChannelState prev = nextChannels.get(channel);
            if (prev == null) {
                continue;
            }
            Object value = entry.getValue();
            int brightness = prev.brightness();
            int colorTemp  = prev.colorTemperature();
            if (value instanceof Map<?, ?> nested) {
                Object b = nested.get("brightness");
                Object t = nested.get("colorTemperature");
                if (b instanceof Number n) brightness = n.intValue();
                if (t instanceof Number n) colorTemp = n.intValue();
            } else if (value instanceof Number n) {
                brightness = n.intValue();
            }
            nextChannels.put(channel, new ChannelState(channel, brightness, colorTemp));
        }
        DeviceState updated = new DeviceState(
                current.deviceId(),
                nextChannels,
                current.currentMode(),
                current.temperatureCelsius(),
                Instant.now()
        );
        stateByDevice.put(deviceId, updated);
        return new CommandResult(
                deviceId,
                UUID.randomUUID().toString(),
                true,
                params,
                "OK",
                Instant.now()
        );
    }

    /** 模拟"过去 24 小时平均光照强度"。 */
    public double avgLightIntensityLux24h(String deviceId) {
        if (!stateByDevice.containsKey(deviceId)) {
            throw new IllegalArgumentException("Unknown deviceId=" + deviceId);
        }
        return 4200.0;
    }

    /** 模拟"今日定时计划"。 */
    public List<Map<String, Object>> todaySchedule(String deviceId) {
        if (!stateByDevice.containsKey(deviceId)) {
            throw new IllegalArgumentException("Unknown deviceId=" + deviceId);
        }
        return List.of(
                Map.of("time", "08:00", "preset", "sunrise",  "brightness", 30),
                Map.of("time", "12:00", "preset", "normal",   "brightness", 70),
                Map.of("time", "18:00", "preset", "sunset",   "brightness", 40),
                Map.of("time", "22:00", "preset", "moonlight","brightness",  5)
        );
    }
}
