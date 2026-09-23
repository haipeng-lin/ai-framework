package org.happyhai.springai.alibaba.comprehensive.tool;

import org.happyhai.springai.alibaba.comprehensive.domain.DeviceInfo;
import org.happyhai.springai.alibaba.comprehensive.service.DeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.chat.model.ToolContext;

import java.util.List;
import java.util.function.BiFunction;

public class DeviceQueryTool implements BiFunction<DeviceQueryRequest, ToolContext, String> {

    private static final Logger logger = LoggerFactory.getLogger(DeviceQueryTool.class);

    private final DeviceService deviceService;

    public DeviceQueryTool(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @Override
    public String apply(DeviceQueryRequest request, ToolContext context) {
        String userId = request.getUserId();
        logger.info("查询用户设备 - userId: {}, 查询在线设备: {}", userId, request.isOnlineOnly());

        List<DeviceInfo> devices;
        if (request.isOnlineOnly()) {
            devices = deviceService.getOnlineDevicesByUserId(userId);
        } else {
            devices = deviceService.getDevicesByUserId(userId);
        }

        if (devices.isEmpty()) {
            return "未找到设备。请确认用户ID是否正确。";
        }

        StringBuilder response = new StringBuilder();
        response.append("找到 ").append(devices.size()).append(" 个设备：\n");
        for (int i = 0; i < devices.size(); i++) {
            DeviceInfo device = devices.get(i);
            response.append(i + 1).append(". ");
            response.append("设备ID: ").append(device.getDeviceId());
            response.append(", 名称: ").append(device.getDeviceName());
            response.append(", 产品码: ").append(device.getProductCode());
            response.append(", 状态: ").append(device.isOnline() ? "在线" : "离线");
            response.append("\n");
        }
        response.append("请选择要操作的设备（回复设备ID）。");

        logger.info("返回设备列表: {}", response);
        return response.toString();
    }

    public static FunctionToolCallback create(DeviceService deviceService) {
        return FunctionToolCallback.builder("device_query", new DeviceQueryTool(deviceService))
                .description("查询用户的设备列表。当用户想要控制设备时，先使用此工具查询用户的设备信息，包括设备ID、名称、在线状态等")
                .inputType(DeviceQueryRequest.class)
                .build();
    }
}
