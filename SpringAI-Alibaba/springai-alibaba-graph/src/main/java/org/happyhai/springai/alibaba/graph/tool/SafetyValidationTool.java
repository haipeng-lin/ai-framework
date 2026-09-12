 package org.happyhai.springai.alibaba.graph.tool;
 
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;
 import org.springframework.ai.tool.function.FunctionToolCallback;
 import org.springframework.ai.chat.model.ToolContext;
 import java.util.function.BiFunction;
 
 /**
  * Safety Validation Tool - validates explicit control commands.
  * Used by the explicit_control node.
  */
 public class SafetyValidationTool implements BiFunction<SafetyValidationTool.Request, ToolContext, String> {
     private static final Logger log = LoggerFactory.getLogger(SafetyValidationTool.class);
 
     @Override
     public String apply(Request req, ToolContext ctx) {
         log.info("[SafetyValidation] deviceId={}, command={}, brightness={}",
                 req.deviceId, req.command, req.brightness);
 
         if (req.deviceId == null || req.deviceId.isBlank())
             return fail("DEVICE_ID_INVALID", "设备ID不能为空");
         if (req.command == null || req.command.isBlank())
             return fail("COMMAND_INVALID", "控制指令不能为空");
         if (req.brightness != null) {
             try {
                 int b = Integer.parseInt(req.brightness);
                 if (b < 0 || b > 100) return fail("BRIGHTNESS_OOR", "亮度值必须在0-100之间");
             } catch (NumberFormatException e) {
                 return fail("BRIGHTNESS_FMT", "亮度值格式错误");
             }
         }
         if (req.deviceId.contains("emergency"))
             return fail("SAFETY_VIOLATION", "紧急模式设备需要特殊授权");
 
         log.info("[SafetyValidation] 校验通过");
         return ok(req.deviceId);
     }
 
     private String ok(String deviceId) {
         return String.format(
             "{\"passed\":true,\"validatedDeviceId\":\"%s\"," +
             "\"message\":\"安全校验通过，可执行硬件命令\"}", deviceId);
     }
     private String fail(String code, String msg) {
         return String.format(
             "{\"passed\":false,\"code\":\"%s\",\"message\":\"%s\"}", code, msg);
     }
 
     public static FunctionToolCallback create() {
         return FunctionToolCallback.builder("safety_validation",
                 new SafetyValidationTool())
             .description("水族灯安全校验。校验设备ID合法性、指令参数范围(亮度0-100)、危险操作拦截。")
             .inputType(Request.class).build();
     }
     public static class Request {
         private String deviceId;
         private String command;
         private String brightness;
         public String getDeviceId() { return deviceId; }
         public void setDeviceId(String v) { this.deviceId = v; }
         public String getCommand() { return command; }
         public void setCommand(String v) { this.command = v; }
         public String getBrightness() { return brightness; }
         public void setBrightness(String v) { this.brightness = v; }
     }
 }
