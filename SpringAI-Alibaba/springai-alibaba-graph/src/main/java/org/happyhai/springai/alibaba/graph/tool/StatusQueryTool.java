 package org.happyhai.springai.alibaba.graph.tool;
 
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;
 import org.springframework.ai.tool.function.FunctionToolCallback;
 import org.springframework.ai.chat.model.ToolContext;
 import java.util.function.BiFunction;
 
 /**
  * Status Query Tool - queries device status.
  * Used by the status_query node.
  */
 public class StatusQueryTool implements BiFunction<StatusQueryTool.Request, ToolContext, String> {
     private static final Logger log = LoggerFactory.getLogger(StatusQueryTool.class);
 
     @Override
     public String apply(Request req, ToolContext ctx) {
         log.info("[StatusQuery] deviceId={}, queryType={}", req.deviceId, req.queryType);
         if (req.deviceId == null || req.deviceId.isBlank())
             return "{\"success\":false,\"error\":\"设备ID不能为空\"}";
         String qt = req.queryType;
         if ("power".equalsIgnoreCase(qt))
             return ok("power", "on");
         if ("brightness".equalsIgnoreCase(qt))
             return ok("brightness", 65);
         if ("network".equalsIgnoreCase(qt))
             return ok("networkStatus", "online", "signalStrength", 85);
         return String.format(
             "{\"success\":true,\"deviceId\":\"%s\"," +
             "\"power\":\"on\",\"brightness\":65,\"colorTemp\":50," +
             "\"networkStatus\":\"online\",\"signalStrength\":85," +
             "\"firmwareVersion\":\"v2.3.1\"," +
             "\"message\":\"完整状态查询完成\"}", req.deviceId);
     }
     private String ok(Object... kvs) {
         StringBuilder s = new StringBuilder("{\"success\":true,");
         for (int i = 0; i < kvs.length; i += 2)
             s.append("\"").append(kvs[i]).append("\":").append(kvs[i+1]).append(",");
         s.setLength(s.length()-1);
         s.append("}");
         return s.toString();
     }
     public static FunctionToolCallback create() {
         return FunctionToolCallback.builder("status_query", new StatusQueryTool())
             .description("水族灯状态查询工具。查询电源状态/亮度/色温/网络状态/信号强度/固件版本。")
             .inputType(Request.class).build();
     }
     public static class Request {
         private String deviceId;
         private String queryType;
         public String getDeviceId() { return deviceId; }
         public void setDeviceId(String v) { this.deviceId = v; }
         public String getQueryType() { return queryType; }
         public void setQueryType(String v) { this.queryType = v; }
     }
 }
