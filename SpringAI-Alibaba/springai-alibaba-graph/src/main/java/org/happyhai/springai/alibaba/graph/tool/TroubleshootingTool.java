 package org.happyhai.springai.alibaba.graph.tool;
 
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;
 import org.springframework.ai.tool.function.FunctionToolCallback;
 import org.springframework.ai.chat.model.ToolContext;
 import java.util.function.BiFunction;
 
 /**
  * Troubleshooting Tool - diagnoses device issues.
  * Used by the troubleshooting node.
  */
 public class TroubleshootingTool implements BiFunction<TroubleshootingTool.Request, ToolContext, String> {
     private static final Logger log = LoggerFactory.getLogger(TroubleshootingTool.class);
 
     @Override
     public String apply(Request req, ToolContext ctx) {
         log.info("[Troubleshooting] issueType={}, deviceId={}, symptom={}",
                 req.issueType, req.deviceId, req.symptom);
         String type = req.issueType != null ? req.issueType : inferType(req.symptom);
         String[] steps = stepsFor(type);
         StringBuilder sb = new StringBuilder();
         sb.append("{\"success\":true,\"issueType\":\"").append(type).append("\"");
         sb.append(",\"diagnosis\":\"").append(diagnosisOf(type)).append("\"");
         sb.append(",\"steps\":[");
         for (int i = 0; i < steps.length; i++) {
             if (i > 0) sb.append(",");
             sb.append("\"").append(steps[i].replace("\"", "\\\"").replace("\n", "")).append("\"");
         }
         sb.append("],\"message\":\"故障诊断完成\"}");
         return sb.toString();
     }
 
     private String inferType(String symptom) {
         if (symptom == null) return "unknown";
         String m = symptom.toLowerCase();
         if (m.contains("离线") || m.contains("offline")) return "device_offline";
         if (m.contains("连不上") || m.contains("connect")) return "connection_failed";
         if (m.contains("激活")) return "activation_failed";
         if (m.contains("mqtt") || m.contains("断开")) return "mqtt_disconnected";
         if (m.contains("无响应") || m.contains("不亮")) return "light_not_responding";
         if (m.contains("wifi") || m.contains("信号")) return "unstable_wifi";
         return "unknown";
     }
     private String diagnosisOf(String type) {
         return switch (type) {
             case "device_offline" -> "设备离线";
             case "connection_failed" -> "连接失败";
             case "activation_failed" -> "设备激活失败";
             case "mqtt_disconnected" -> "MQTT连接断开";
             case "light_not_responding" -> "灯光无响应";
             case "unstable_wifi" -> "WiFi连接不稳定";
             default -> "未知问题";
         };
     }
     private String[] stepsFor(String type) {
         return switch (type) {
             case "device_offline" -> new String[]{
                 "1. 检查设备电源，指示灯是否亮起",
                 "2. 确认WiFi信号强度≥50%",
                 "3. 重启路由器和设备，等待2分钟",
                 "4. 在App中删除设备后重新添加配网",
                 "5. 如长期离线可能是固件损坏，联系售后"
             };
             case "connection_failed" -> new String[]{
                 "1. 确认手机蓝牙已开启且权限已授权",
                 "2. 设备是否已进入配网模式（指示灯快闪）",
                 "3. 切换到2.4G WiFi（非5G）",
                 "4. 关闭手机VPN后重试配网",
                 "5. 确认路由器未开启AP隔离"
             };
             case "activation_failed" -> new String[]{
                 "1. 设备激活需在通电后5分钟内完成",
                 "2. 确认设备未在其他账号下被激活",
                 "3. 切换到稳定的家庭WiFi",
                 "4. 清除App缓存后重试",
                 "5. 如提示「设备已被他人绑定」，联系客服"
             };
             case "mqtt_disconnected" -> new String[]{
                 "1. MQTT断开通常由网络不稳定导致",
                 "2. 检查路由器是否定时重启",
                 "3. 确认设备固件为最新版本",
                 "4. 确保1883/8883端口开放",
                 "5. 重启设备等待MQTT重连（通常<30秒）"
             };
             case "light_not_responding" -> new String[]{
                 "1. 确认设备电源正常，指示灯状态正常",
                 "2. 使用实体按键手动开关灯测试",
                 "3. 检查App中设备在线状态",
                 "4. 尝试恢复出厂设置：断电30秒后重新通电",
                 "5. 如手动和App都无法控制，联系维修"
             };
             case "unstable_wifi" -> new String[]{
                 "1. 设备建议连接2.4GHz WiFi",
                 "2. WiFi密码避免特殊字符",
                 "3. 确认路由器连接设备数量不过多（≤20台）",
                 "4. 将设备放置在路由器1-5米范围内",
                 "5. 更换WiFi信道（1/6/11）"
             };
             default -> new String[]{
                 "1. 请详细描述您遇到的问题和设备表现",
                 "2. 提供设备型号、固件版本、App版本",
                 "3. 记录问题发生的时间、操作步骤",
                 "4. 联系官方客服：400-XXX-XXXX",
                 "5. 提供设备ID以便进一步排查"
             };
         };
     }
 
     public static FunctionToolCallback create() {
         return FunctionToolCallback.builder("troubleshooting", new TroubleshootingTool())
             .description("水族灯故障排查工具。诊断设备离线/连接失败/激活问题/MQTT断开/灯光无响应/WiFi不稳定等问题。")
             .inputType(Request.class).build();
     }
     public static class Request {
         private String deviceId;
         private String issueType;
         private String symptom;
         public String getDeviceId() { return deviceId; }
         public void setDeviceId(String v) { this.deviceId = v; }
         public String getIssueType() { return issueType; }
         public void setIssueType(String v) { this.issueType = v; }
         public String getSymptom() { return symptom; }
         public void setSymptom(String v) { this.symptom = v; }
     }
 }
