package org.happyhai.agentscope.classify.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.happyhai.agentscope.classify.domain.DeviceState;
import org.happyhai.agentscope.classify.service.InMemoryDeviceService;
import org.springframework.stereotype.Component;

/** 查询设备当前快照，主要服务于 status_query。 */
@Component
public class QueryDeviceStatusTool {

    private final InMemoryDeviceService deviceService;

    public QueryDeviceStatusTool(InMemoryDeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @Tool(
            name = "query_device_status",
            description = "查询水族灯设备当前的完整状态快照（通道、模式、温度等）。",
            readOnly = true,
            concurrencySafe = true
    )
    public DeviceState query(
            @ToolParam(name = "device_id", description = "设备 ID", required = true)
                    String deviceId) {
        return deviceService.fetchState(deviceId);
    }
}
