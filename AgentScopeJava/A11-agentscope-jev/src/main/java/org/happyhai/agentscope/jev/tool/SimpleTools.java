package org.happyhai.agentscope.jev.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class SimpleTools {

    @Tool(
            name = "get_current_time",
            description = "Returns the current time in a given IANA timezone.",
            readOnly = true,
            concurrencySafe = true)
    public String getCurrentTime(
            @ToolParam(name = "timezone", description = "IANA timezone, e.g. Asia/Shanghai")
                    String timezone) {
        return LocalDateTime.now(ZoneId.of(timezone))
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    @Tool(
            name = "echo",
            description = "Returns the input string verbatim. Useful for verifying onActing flow.",
            readOnly = true,
            concurrencySafe = true)
    public String echo(
            @ToolParam(name = "text", description = "Text to echo back")
                    String text) {
        return text == null ? "" : text;
    }

}
