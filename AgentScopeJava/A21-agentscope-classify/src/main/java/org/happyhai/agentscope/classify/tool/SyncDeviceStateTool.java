package org.happyhai.agentscope.classify.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.happyhai.agentscope.classify.domain.DeviceState;
import org.happyhai.agentscope.classify.service.InMemoryDeviceService;
import org.springframework.stereotype.Component;

/**
 * 设备状态同步工具（对应设计稿 4.1 节）。
 *
 * <p>模糊控制、状态查询等子 Agent 都需要先看最新设备状态再决策，
 * 所以这是一个共享工具，由所有需要的 Agent 装配进自己的 Toolkit。
 */
@Component
public class SyncDeviceStateTool {

    private final InMemoryDeviceService deviceService;

    public SyncDeviceStateTool(InMemoryDeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @Tool(
            name = "sync_device_state",
            description = "从水族灯设备同步最新状态，包括各通道亮度、色温、运行模式、灯具温度等。"
                        + "模糊控制和状态查询应先调用本工具，避免重复拉取。",
            readOnly = true,
            concurrencySafe = true
    )
    public DeviceState sync(
            @ToolParam(name = "device_id",
                       description = "设备 ID，例如 light-001",
                       required = true)
                    String deviceId) {
        return deviceService.fetchState(deviceId);
    }
}
