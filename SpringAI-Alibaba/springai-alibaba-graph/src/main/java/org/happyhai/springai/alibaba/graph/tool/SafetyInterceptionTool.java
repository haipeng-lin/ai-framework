 package org.happyhai.springai.alibaba.graph.tool;
 
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;
 import org.springframework.ai.tool.function.FunctionToolCallback;
 import org.springframework.ai.chat.model.ToolContext;
 import java.util.function.BiFunction;
 
 /**
  * Safety Interception Tool - blocks dangerous commands.
  * Used by the safety_violation node.
  */
 public class SafetyInterceptionTool implements BiFunction<SafetyInterceptionTool.Request, ToolContext, String> {
     private static final Logger log = LoggerFactory.getLogger(SafetyInterceptionTool.class);
 
     @Override
     public String apply(Request req, ToolContext ctx) {
         log.warn("[SafetyInterception] 检测到安全违规 - command={}, deviceId={}",
                 req.command, req.deviceId);
         String code = detectViolation(req.command, req.deviceId, req.brightness);
         if (code == null) {
             log.info("[SafetyInterception] 校验通过");
             return ok(req.command, req.deviceId);
         }
         return block(code, req.command, req.deviceId);
     }
 
     private String detectViolation(String cmd, String deviceId, String brightness) {
         if (cmd != null && (cmd.toLowerCase().contains("emergency") ||
             cmd.toLowerCase().contains("bypass") ||
             cmd.toLowerCase().contains("force") && cmd.toLowerCase().contains("override")))
             return "EMERGENCY_OVERRIDE";
         if (brightness != null) {
             try {
                 int b = Integer.parseInt(brightness);
                 if (b < 0 || b > 100) return "BRIGHTNESS_OUT_OF_RANGE";
             } catch (NumberFormatException e) { return "BRIGHTNESS_FORMAT_ERROR"; }
         }
         if (deviceId != null && (deviceId.contains("all_device") || deviceId.contains("*")))
             return "BROADCAST_NOT_ALLOWED";
         if (cmd != null && cmd.toLowerCase().contains("bypass"))
             return "BYPASS_ATTEMPT";
         return null;
     }
 
     private String ok(String cmd, String deviceId) {
         return String.format(
             "{\"passed\":true,\"command\":\"%s\",\"deviceId\":\"%s\"," +
             "\"message\":\"安全检查通过\"}", cmd, deviceId);
     }
     private String block(String code, String cmd, String deviceId) {
         String msg = switch (code) {
             case "EMERGENCY_OVERRIDE" -> "紧急模式需要特殊授权";
             case "BRIGHTNESS_OUT_OF_RANGE" -> "亮度超出0-100范围";
             case "BRIGHTNESS_FORMAT_ERROR" -> "亮度参数格式错误";
             case "BROADCAST_NOT_ALLOWED" -> "广播控制已被安全策略拦截";
             case "BYPASS_ATTEMPT" -> "安全绕过尝试已被拦截";
             default -> "未知安全违规";
         };
         return String.format(
             "{\"passed\":false,\"violationCode\":\"%s\"," +
             "\"message\":\"%s\",\"command\":\"%s\",\"deviceId\":\"%s\"," +
             "\"interceptedAt\":\"%s\"}",
             code, msg, cmd, deviceId, java.time.Instant.now().toString());
     }
 
     public static FunctionToolCallback create() {
         return FunctionToolCallback.builder("safety_interception", new SafetyInterceptionTool())
             .description("水族灯安全拦截工具。检测紧急越权、亮度超范围、广播控制、安全绕过等危险命令并拦截。")
             .inputType(Request.class).build();
     }
     public static class Request {
         private String command;
         private String deviceId;
         private String brightness;
         public String getCommand() { return command; }
         public void setCommand(String v) { this.command = v; }
         public String getDeviceId() { return deviceId; }
         public void setDeviceId(String v) { this.deviceId = v; }
         public String getBrightness() { return brightness; }
         public void setBrightness(String v) { this.brightness = v; }
     }
 }
