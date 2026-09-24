package org.happyhai.agentscope.classify.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.happyhai.agentscope.classify.service.InMemoryDeviceService;
import org.springframework.stereotype.Component;

/** 查询设备历史运行数据（设计稿 3.5 节所需工具）。 */
@Component
public class QueryDeviceHistoryTool {

    private final InMemoryDeviceService deviceService;

    public QueryDeviceHistoryTool(InMemoryDeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @Tool(
            name = "query_device_history",
            description = "查询水族灯设备过去一段时间的运行数据摘要，例如平均光照强度。",
            readOnly = true,
            concurrencySafe = true
    )
    public String history(
            @ToolParam(name = "device_id", description = "设备 ID", required = true)
                    String deviceId) {
        double lux = deviceService.avgLightIntensityLux24h(deviceId);
        return "过去 24 小时平均光照强度约 " + lux + " lux";
    }
}
