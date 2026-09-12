 package org.happyhai.springai.alibaba.graph.tool;
 
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;
 import org.springframework.ai.tool.function.FunctionToolCallback;
 import org.springframework.ai.chat.model.ToolContext;
 import java.util.*;
 import java.util.concurrent.ConcurrentHashMap;
 import java.util.function.BiFunction;
 
 /**
  * Memory Management Tool - stores and retrieves user preferences.
  * Used by the memory_management node.
  */
 public class MemoryManagementTool implements BiFunction<MemoryManagementTool.Request, ToolContext, String> {
     private static final Logger log = LoggerFactory.getLogger(MemoryManagementTool.class);
     private static final Map<String, Map<String, Object>> STORE = new ConcurrentHashMap<>();
 
     @Override
     public String apply(Request req, ToolContext ctx) {
         log.info("[Memory] op={}, userId={}, key={}", req.operation, req.userId, req.key);
         if (req.userId == null || req.userId.isBlank())
             return "{\"success\":false,\"error\":\"用户ID不能为空\"}";
         STORE.putIfAbsent(req.userId, new ConcurrentHashMap<>());
         Map<String, Object> mem = STORE.get(req.userId);
         return switch (req.operation.toLowerCase()) {
             case "save" -> handleSave(mem, req.key, req.value, req.deviceId);
             case "recall" -> handleRecall(mem, req.key);
             case "list" -> handleList(mem);
             case "delete" -> handleDelete(mem, req.key);
             default -> "{\"success\":false,\"error\":\"未知操作: " + req.operation + "\"}";
         };
     }
 
     @SuppressWarnings("unchecked")
     private String handleSave(Map<String, Object> m, String key, String value, String deviceId) {
         if (key == null) return fail("key不能为空");
         Map<String, Object> rec = new LinkedHashMap<>();
         rec.put("value", value); rec.put("deviceId", deviceId);
         rec.put("timestamp", java.time.Instant.now().toString());
         m.put(key, rec);
         return ok("记忆已保存: " + key);
     }
     private String handleRecall(Map<String, Object> m, String key) {
         if (key == null) return handleList(m);
         Object v = m.get(key);
         if (v == null) return fail("未找到: " + key);
         @SuppressWarnings("unchecked") Map<String, Object> rec = (Map<String, Object>) v;
         return String.format(
             "{\"success\":true,\"operation\":\"recall\",\"key\":\"%s\"," +
             "\"value\":\"%s\",\"deviceId\":\"%s\",\"timestamp\":\"%s\"}",
             key, rec.get("value"), rec.get("deviceId"), rec.get("timestamp"));
     }
     private String handleList(Map<String, Object> m) {
         StringBuilder sb = new StringBuilder("{\"success\":true,\"operation\":\"list\",\"memories\":{");
         int i = 0;
         for (var e : m.entrySet()) {
             if (i++ > 0) sb.append(",");
             @SuppressWarnings("unchecked") Map<String, Object> r = (Map<String, Object>) e.getValue();
             sb.append("\"").append(e.getKey()).append("\":{")
               .append("\"value\":\"").append(r.get("value")).append("\",")
               .append("\"deviceId\":\"").append(r.get("deviceId")).append("\",")
               .append("\"timestamp\":\"").append(r.get("timestamp")).append("\"}");
         }
         sb.append("}}");
         return sb.toString();
     }
     private String handleDelete(Map<String, Object> m, String key) {
         if (key == null) { m.clear(); return ok("所有记忆已清除"); }
         return m.remove(key) != null ? ok("已删除: " + key) : fail("key不存在: " + key);
     }
     private String ok(String msg) { return "{\"success\":true,\"message\":\"" + msg + "\"}"; }
     private String fail(String msg) { return "{\"success\":false,\"message\":\"" + msg + "\"}"; }
 
     public static FunctionToolCallback create() {
         return FunctionToolCallback.builder("memory_management", new MemoryManagementTool())
             .description("水族灯长期记忆管理工具。save/recall/list/delete 用户灯光偏好和配置。")
             .inputType(Request.class).build();
     }
     public static class Request {
         private String userId;
         private String operation;
         private String key;
         private String value;
         private String deviceId;
         public String getUserId() { return userId; }
         public void setUserId(String v) { this.userId = v; }
         public String getOperation() { return operation; }
         public void setOperation(String v) { this.operation = v; }
         public String getKey() { return key; }
         public void setKey(String v) { this.key = v; }
         public String getValue() { return value; }
         public void setValue(String v) { this.value = v; }
         public String getDeviceId() { return deviceId; }
         public void setDeviceId(String v) { this.deviceId = v; }
     }
 }
