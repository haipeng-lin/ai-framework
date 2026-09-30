package org.happyhai.agentscope.mcp.server.tool;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.zone.ZoneRulesException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 纯 Java 工具实现，不依赖任何 AgentScope 注解。
 * 与 A04-agentscope-tool 中的 SimpleTools 行为保持一致，
 * 但这里只是普通方法，由 McpServerConfig 包装成 MCP 工具对外发布。
 */
public class TimeTools {

    public Map<String, Object> getCurrentTime(String format, String timezone) {
        String resolvedFormat = (format == null || format.isBlank()) ? "yyyy-MM-dd HH:mm:ss" : format;
        String resolvedZone = (timezone == null || timezone.isBlank()) ? "Asia/Shanghai" : timezone;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("format", resolvedFormat);
        payload.put("timezone", resolvedZone);

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(resolvedFormat);
            String formatted = LocalDateTime.now(ZoneId.of(resolvedZone)).format(formatter);
            payload.put("ok", true);
            payload.put("time", formatted);
            return payload;
        } catch (ZoneRulesException ex) {
            payload.put("ok", false);
            payload.put("error", "unknown timezone: " + resolvedZone);
            return payload;
        } catch (IllegalArgumentException ex) {
            payload.put("ok", false);
            payload.put("error", "invalid format pattern: " + resolvedFormat);
            return payload;
        }
    }
}
