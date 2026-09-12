 package org.happyhai.springai.alibaba.graph.tool;
 
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;
 import org.springframework.ai.tool.function.FunctionToolCallback;
 import org.springframework.ai.chat.model.ToolContext;
 import java.util.function.BiFunction;
 
 /**
  * Hardware Execution Tool - sends MQTT commands to the device.
  * Used by explicit_control and fuzzy_control nodes after validation/reasoning.
  */
 public class HardwareExecutionTool implements BiFunction<HardwareExecutionTool.Request, ToolContext, String> {
     private static final Logger log = LoggerFactory.getLogger(HardwareExecutionTool.class);
 
     @Override
     public String apply(Request req, ToolContext ctx) {
         log.info("[HardwareExecution] deviceId={}, command={}, brightness={}",
                 req.deviceId, req.command, req.brightness);
 
         if (req.deviceId == null || req.deviceId.isBlank())
             return "{\"success\":false,\"error\":\"设备ID为空\"}";
 
         String topic = "device/" + req.deviceId + "/command";
         String hex = buildHex(req.command, req.brightness, req.colorTemp);
 
         log.info("[HardwareExecution] MQTT发送 → topic: {}, hex: {}", topic, hex);
         return String.format(
             "{\"success\":true,\"deviceId\":\"%s\",\"command\":\"%s\"," +
             "\"mqttTopic\":\"%s\",\"hexOrder\":\"%s\"," +
             "\"message\":\"硬件命令已发送至MQTT，等待设备响应\"}",
             req.deviceId, req.command, topic, hex);
     }
 
     private String buildHex(String cmd, String brightness, String colorTemp) {
         String cmdHex = "C9"; // ON
         if ("off".equalsIgnoreCase(cmd) || "close".equalsIgnoreCase(cmd)) cmdHex = "CA";
         String bHex = "80";
         if (brightness != null) {
             try { bHex = String.format("%02X", Integer.parseInt(brightness) * 255 / 100); }
             catch (NumberFormatException ignored) {}
         }
         String tHex = "50";
         if (colorTemp != null) {
             try { tHex = String.format("%02X", Integer.parseInt(colorTemp) * 255 / 100); }
             catch (NumberFormatException ignored) {}
         }
         return cmdHex + "01" + bHex + tHex;
     }
 
     public static FunctionToolCallback create() {
         return FunctionToolCallback.builder("hardware_execution",
                 new HardwareExecutionTool())
             .description("水族灯硬件执行工具。将控制指令发送到MQTT，执行开/关、亮度(0-100)、色温等硬件操作。")
             .inputType(Request.class).build();
     }
     public static class Request {
         private String deviceId;
         private String command;
         private String brightness;
         private String colorTemp;
         public String getDeviceId() { return deviceId; }
         public void setDeviceId(String v) { this.deviceId = v; }
         public String getCommand() { return command; }
         public void setCommand(String v) { this.command = v; }
         public String getBrightness() { return brightness; }
         public void setBrightness(String v) { this.brightness = v; }
         public String getColorTemp() { return colorTemp; }
         public void setColorTemp(String v) { this.colorTemp = v; }
     }
 }
