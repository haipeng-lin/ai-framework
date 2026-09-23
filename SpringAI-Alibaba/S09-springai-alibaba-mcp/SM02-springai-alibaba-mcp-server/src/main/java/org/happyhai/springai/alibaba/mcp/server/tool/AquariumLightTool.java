package org.happyhai.springai.alibaba.mcp.server.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 水族灯控制(模拟) —— 与项目已有业务域对应,真实硬件接入替换 Mock 部分即可。
 */
@Component
public class AquariumLightTool {

    private static final List<String> MODES = List.of("日出", "日落", "夜间", "珊瑚生长", "全开");

    @Tool(description = "列出水族灯支持的所有预设模式。")
    public List<String> listModes() {
        return MODES;
    }

    @Tool(description = "控制水族灯切换到指定模式与亮度(模拟实现)。返回执行结果摘要。")
    public Map<String, Object> controlLight(
            @ToolParam(description = "水族灯设备 ID,例如 aquarium-1") String deviceId,
            @ToolParam(description = "目标模式,可选值: 日出 / 日落 / 夜间 / 珊瑚生长 / 全开") String mode,
            @ToolParam(description = "亮度百分比,0-100") int brightness) {
        if (!MODES.contains(mode)) {
            return Map.of(
                    "ok", false,
                    "error", "unsupported mode: " + mode,
                    "supported", MODES
            );
        }
        if (brightness < 0 || brightness > 100) {
            return Map.of(
                    "ok", false,
                    "error", "brightness must be in [0,100], got " + brightness
            );
        }
        return Map.of(
                "ok", true,
                "deviceId", deviceId,
                "mode", mode,
                "brightness", brightness,
                "appliedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }

    @Tool(description = "查询水族灯当前状态(模拟实现)。")
    public Map<String, Object> getLightStatus(
            @ToolParam(description = "水族灯设备 ID") String deviceId) {
        return Map.of(
                "deviceId", deviceId,
                "online", true,
                "mode", "日落",
                "brightness", 30,
                "uptimeHours", 128
        );
    }
}