package org.happyhai.springai.alibaba.comprehensive.service;

import org.happyhai.springai.alibaba.comprehensive.domain.DeviceInfo;
import org.happyhai.springai.alibaba.comprehensive.domain.LightCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DeviceService {

    private static final Logger logger = LoggerFactory.getLogger(DeviceService.class);

    private static final Map<String, LightCommand> LIGHT_COMMANDS = new HashMap<>();
    private static final Map<String, DeviceInfo> USER_DEVICES = new HashMap<>();

    static {
        LIGHT_COMMANDS.put("0x0102A201", new LightCommand(
                "0x0102A201",
                "水族灯 Pro",
                "C90102A2010A0602001D",
                "C90102A2010A211D"
        ));
        LIGHT_COMMANDS.put("0x0102A202", new LightCommand(
                "0x0102A202",
                "水族灯 Mini",
                "C90102A2020A0602002D",
                "C90102A2020A212D"
        ));
        LIGHT_COMMANDS.put("0x0102A203", new LightCommand(
                "0x0102A203",
                "全光谱水族灯",
                "C90102A2030A0602033D",
                "C90102A2030A213D"
        ));

        USER_DEVICES.put("user001_device_001", new DeviceInfo(
                "device001", "0x0102A201", "主缸灯", true, "user001"
        ));
        USER_DEVICES.put("user001_device_002", new DeviceInfo(
                "device002", "0x0102A202", "副缸灯", true, "user001"
        ));
        USER_DEVICES.put("user001_device_003", new DeviceInfo(
                "device003", "0x0102A203", "珊瑚缸灯", false, "user001"
        ));
        USER_DEVICES.put("user002_device_001", new DeviceInfo(
                "device101", "0x0102A201", "客厅水族灯", true, "user002"
        ));
    }

    public List<DeviceInfo> getDevicesByUserId(String userId) {
        logger.info("查询用户设备 - userId: {}", userId);
        List<DeviceInfo> devices = new ArrayList<>();
        for (DeviceInfo device : USER_DEVICES.values()) {
            if (device.getUserId().equals(userId)) {
                devices.add(device);
            }
        }
        logger.info("找到设备数量: {}, 设备列表: {}", devices.size(), devices);
        return devices;
    }

    public List<DeviceInfo> getOnlineDevicesByUserId(String userId) {
        logger.info("查询用户在线设备 - userId: {}", userId);
        List<DeviceInfo> devices = new ArrayList<>();
        for (DeviceInfo device : USER_DEVICES.values()) {
            if (device.getUserId().equals(userId) && device.isOnline()) {
                devices.add(device);
            }
        }
        logger.info("找到在线设备数量: {}, 设备列表: {}", devices.size(), devices);
        return devices;
    }

    public Optional<DeviceInfo> getDeviceById(String deviceId) {
        for (DeviceInfo device : USER_DEVICES.values()) {
            if (device.getDeviceId().equals(deviceId)) {
                return Optional.of(device);
            }
        }
        return Optional.empty();
    }

    public Optional<LightCommand> getLightCommand(String productCode) {
        logger.info("查询产品命令码 - productCode: {}", productCode);
        LightCommand command = LIGHT_COMMANDS.get(productCode);
        if (command == null) {
            logger.warn("未找到产品命令码: {}", productCode);
        }
        return Optional.ofNullable(command);
    }

    public String getProductCodeByDeviceId(String deviceId) {
        Optional<DeviceInfo> device = getDeviceById(deviceId);
        return device.map(DeviceInfo::getProductCode).orElse(null);
    }

    public String getProductNameByDeviceId(String deviceId) {
        Optional<DeviceInfo> device = getDeviceById(deviceId);
        return device.map(DeviceInfo::getDeviceName).orElse(null);
    }

    public Map<String, LightCommand> getAllLightCommands() {
        return new HashMap<>(LIGHT_COMMANDS);
    }

    public List<DeviceInfo> getAllMockDevices() {
        return new ArrayList<>(USER_DEVICES.values());
    }
}
