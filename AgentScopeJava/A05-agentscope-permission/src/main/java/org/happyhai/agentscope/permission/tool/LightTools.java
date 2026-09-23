package org.happyhai.agentscope.permission.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 调灯工具 —— @Tool 注解形式。
 * 物理操作，需要在 AgentConfig.permissionContext 里给 toggle_light 加 ASK 规则才能触发人工确认。
 */
public class LightTools {

    private static final Logger log = LoggerFactory.getLogger(LightTools.class);

    @Tool(
            name = "toggle_light",
            description = "切换指定设备的灯为开/关。受 permission 系统保护，每次调用都会暂停等用户确认后才会真正执行。",
            readOnly = false,
            concurrencySafe = false)
    public String toggleLight(
            @ToolParam(name = "deviceId", description = "目标设备 ID，例如 dev-001")
                    String deviceId,
            @ToolParam(name = "action", description = "on 或 off")
                    String action) {
        log.info("[toggle_light] 开始切换 —— deviceId={}, action={}", deviceId, action);
        try {
            // 模拟实际调灯的耗时
            Thread.sleep(100);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        log.info("[toggle_light] 切换完成 —— deviceId={}, action={}, ts={}",
                deviceId, action, System.currentTimeMillis());
        return String.format("\u2713 deviceId=%s 已切换为 %s", deviceId, action);
    }

}
