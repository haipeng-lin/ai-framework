package org.happyhai.agentscope.classify.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.happyhai.agentscope.classify.domain.CommandResult;
import org.happyhai.agentscope.classify.service.InMemoryDeviceService;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 下发控制指令到设备（对应设计稿 3.3 节 Step 3）。 */
@Component
public class SendControlCmdTool {

    private final InMemoryDeviceService deviceService;

    public SendControlCmdTool(InMemoryDeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @Tool(
            name = "send_control_cmd",
            description = "向水族灯设备下发控制参数；参数应当已经通过 validate_params 校验。"
                        + "返回执行是否成功以及 applied_params。",
            concurrencySafe = true
    )
    public CommandResult send(
            @ToolParam(name = "device_id", description = "设备 ID", required = true)
                    String deviceId,
            @ToolParam(name = "params",
                       description = "控制参数字典（已校验/已兜底）",
                       required = true)
                    Map<String, Object> params) {
        return deviceService.applyCommand(deviceId, params);
    }
}
