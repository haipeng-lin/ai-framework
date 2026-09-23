package org.happyhai.springai.alibaba.comprehensive.tool;

import org.happyhai.springai.alibaba.comprehensive.domain.DeviceInfo;
import org.happyhai.springai.alibaba.comprehensive.service.DeviceService;
import org.happyhai.springai.alibaba.comprehensive.service.TraceInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.chat.model.ToolContext;

import java.util.Optional;
import java.util.function.BiFunction;

public class LightControlTool implements BiFunction<LightControlRequest, ToolContext, String> {

    private static final Logger logger = LoggerFactory.getLogger(LightControlTool.class);

    private final DeviceService deviceService;
    private final TraceInfoService traceInfoService;

    public LightControlTool(DeviceService deviceService, TraceInfoService traceInfoService) {
        this.deviceService = deviceService;
        this.traceInfoService = traceInfoService;
    }

    @Override
    public String apply(LightControlRequest request, ToolContext context) {
        String deviceId = request.getDeviceId();
        String command = request.getCommand();
        String traceId = null;

        if (context != null && context.getContext() != null) {
            traceId = (String) context.getContext().get("traceId");
        }

        logger.info("灯光控制工具被调用 - deviceId: {}, command: {}, traceId: {}", deviceId, command, traceId);

        Optional<DeviceInfo> deviceOpt = deviceService.getDeviceById(deviceId);
        
        String productCode = deviceOpt.map(DeviceInfo::getProductCode).orElse(null);
        String deviceName = deviceOpt.map(DeviceInfo::getDeviceName).orElse("未知设备");

        if (traceId != null && traceInfoService != null) {
            traceInfoService.updateLightControl(traceId, deviceId, deviceName, command, productCode);
        }

        return String.format(
                "[PENDING_CONFIRM] 灯光控制操作待确认：\n" +
                "- 设备ID: %s\n" +
                "- 设备名称: %s\n" +
                "- 命令: %s\n" +
                "- 产品码: %s\n" +
                "TraceId: %s\n" +
                "请通过确认接口确认或取消此操作。",
                deviceId, deviceName, command, productCode, traceId
        );
    }

    public static void executeMqttCommand(String deviceId, String command) {
        logger.info("========== MQTT 发送命令 ==========");
        logger.info("目标设备: {}", deviceId);
        logger.info("命令内容: {}", command);
        logger.info("MQTT Topic: device/control/{}", deviceId);
        logger.info("正在建立 MQTT 连接...");
        logger.info("连接成功，发送控制命令...");
        logger.info("命令发送完成，等待设备响应...");
        logger.info("=====================================");
    }

    public static FunctionToolCallback create(DeviceService deviceService, TraceInfoService traceInfoService) {
        return FunctionToolCallback.builder("light_control", new LightControlTool(deviceService, traceInfoService))
                .description("控制水族灯开关和亮度。传入设备ID和命令（开/关）")
                .inputType(LightControlRequest.class)
                .build();
    }
}
