package org.happyhai.agentscope.classify.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.happyhai.agentscope.classify.service.InMemoryDeviceService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 查询设备的定时计划（设计稿 3.5 节所需工具）。 */
@Component
public class QueryDeviceScheduleTool {

    private final InMemoryDeviceService deviceService;

    public QueryDeviceScheduleTool(InMemoryDeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @Tool(
            name = "query_device_schedule",
            description = "查询水族灯设备当日的定时计划，包括每个时段的预设模式和亮度。",
            readOnly = true,
            concurrencySafe = true
    )
    public List<Map<String, Object>> schedule(
            @ToolParam(name = "device_id", description = "设备 ID", required = true)
                    String deviceId) {
        return deviceService.todaySchedule(deviceId);
    }
}
