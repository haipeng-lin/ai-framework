package org.happyhai.agentscope.classify.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.happyhai.agentscope.classify.service.InMemoryDeviceService;
import org.springframework.stereotype.Component;

/** 产品说明书检索（设计稿 3.2 节所需工具）。 */
@Component
public class DeviceManualTool {

    private final InMemoryDeviceService deviceService;

    public DeviceManualTool(InMemoryDeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @Tool(
            name = "device_manual",
            description = "根据关键词检索水族灯设备说明书中的相关章节（通道范围、预设模式等）。",
            readOnly = true,
            concurrencySafe = true
    )
    public String lookup(
            @ToolParam(name = "keyword",
                       description = "检索关键词，例如 主灯 / 预设 / 色温",
                       required = true)
                    String keyword) {
        var cfg = deviceService.config();
        if (keyword == null || keyword.isBlank()) {
            return "请提供具体关键词，例如 主灯 / 色温 / 预设。";
        }
        StringBuilder sb = new StringBuilder();
        for (var entry : cfg.channels().entrySet()) {
            var spec = entry.getValue();
            sb.append("通道 ").append(spec.name())
              .append("：亮度 ").append(spec.minBrightness()).append("-").append(spec.maxBrightness())
              .append("%，色温 ").append(spec.minColorTemperature()).append("-").append(spec.maxColorTemperature())
              .append("K\n");
        }
        sb.append("预设模式：normal/plant/reef/night/sunrise/sunset。");
        return sb.toString();
    }
}
