package org.happyhai.agentscope.permission.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询用户在线设备的工具 —— 注解形式。
 * 这里用一张静态的 mock 表代替真实设备中心，使用 LinkedHashMap 保证 deviceId 顺序稳定。
 */
public class DeviceTools {

    private static final Map<String, List<Map<String, String>>> MOCK_DEVICES = build();

    @Tool(
            name = "query_online_devices",
            description = "查询指定 userId 名下当前在线的设备列表，" +
                    "返回形如 [{deviceId, type, name}, ...] 的字符串。" +
                    "在调用 toggle_light 之前必须先调用本工具拿到合法 deviceId。",
            readOnly = true,
            concurrencySafe = true)
    public String queryOnlineDevices(
            @ToolParam(name = "userId", description = "用户 ID，例如 u1 / u2 / u3")
                    String userId) {
        List<Map<String, String>> devices = MOCK_DEVICES.getOrDefault(userId, List.of());
        if (devices.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < devices.size(); i++) {
            Map<String, String> d = devices.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append('{')
              .append("\"deviceId\":\"").append(d.get("deviceId")).append('"')
              .append(",\"type\":\"").append(d.get("type")).append('"')
              .append(",\"name\":\"").append(d.get("name")).append('"')
              .append('}');
        }
        sb.append(']');
        return sb.toString();
    }

    private static Map<String, List<Map<String, String>>> build() {
        Map<String, List<Map<String, String>>> m = new LinkedHashMap<>();
        m.put("u1", List.of(
                device("dev-001", "light", "客厅灯"),
                device("dev-002", "light", "卧室灯"),
                device("dev-003", "ac",    "客厅空调")
        ));
        m.put("u2", List.of(
                device("dev-101", "light", "书房灯"),
                device("dev-102", "ac",    "主卧空调")
        ));
        m.put("u3", List.of(
                device("dev-201", "light", "玄关灯"),
                device("dev-202", "light", "厨房灯"),
                device("dev-203", "light", "主卧灯"),
                device("dev-204", "light", "卫生间灯")
        ));
        return m;
    }

    private static Map<String, String> device(String id, String type, String name) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("deviceId", id);
        m.put("type", type);
        m.put("name", name);
        return m;
    }

}
