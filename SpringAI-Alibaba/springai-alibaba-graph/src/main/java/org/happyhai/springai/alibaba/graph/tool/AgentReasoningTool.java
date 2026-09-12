 package org.happyhai.springai.alibaba.graph.tool;
 
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;
 import org.springframework.ai.tool.function.FunctionToolCallback;
 import org.springframework.ai.chat.model.ToolContext;
 import java.util.function.BiFunction;
 
 /**
  * Agent Reasoning Tool - reasons about fuzzy control commands.
  * Infers optimal light parameters from time, fish type, and user intent.
  */
 public class AgentReasoningTool implements BiFunction<AgentReasoningTool.Request, ToolContext, String> {
     private static final Logger log = LoggerFactory.getLogger(AgentReasoningTool.class);
 
     @Override
     public String apply(Request req, ToolContext ctx) {
         log.info("[AgentReasoning] message={}, timeOfDay={}, fishType={}",
                 req.message, req.timeOfDay, req.fishType);
         String reasoning = reason(req.message, req.timeOfDay, req.fishType);
         int brightness = extractBrightness(reasoning);
         int colorTemp = extractColorTemp(reasoning);
         return String.format(
             "{\"reasoning\":\"%s\",\"inferredCommand\":\"%s\"," +
             "\"inferredBrightness\":%d,\"inferredColorTemp\":%d," +
             "\"confidence\":0.85,\"message\":\"已推理最优灯光参数\"}",
             reasoning.replace("\\", "\\\\").replace("\"", "\\\""),
             reasoning.contains("开灯") || reasoning.contains("提升") ? "on" : "off",
             brightness, colorTemp);
     }
 
     private String reason(String msg, String time, String fish) {
         StringBuilder sb = new StringBuilder();
         if ("morning".equalsIgnoreCase(time) || "day".equalsIgnoreCase(time))
             sb.append("白天：亮度70%，色温6000K，促进水草光合作用；");
         else if ("evening".equalsIgnoreCase(time))
             sb.append("傍晚：亮度30%，色温3000K，模拟日落；");
         else if ("night".equalsIgnoreCase(time))
             sb.append("夜间：月光模式5%亮度；");
         if (fish != null && fish.contains("海水")) sb.append("海水缸：蓝光为主；");
         else if (fish != null && fish.contains("草缸")) sb.append("草缸：白光+红蓝补光；");
         else if (fish != null && fish.contains("三湖")) sb.append("三湖缸：中高亮度，蓝白光；");
         String m = msg != null ? msg.toLowerCase() : "";
         if (m.contains("舒适") || m.contains("舒服")) sb.append("舒适模式：50%，暖色；");
         if (m.contains("欣赏") || m.contains("拍照")) sb.append("欣赏模式：80%，白光；");
         if (m.contains("喂食")) sb.append("喂食模式：适当调暗；");
         if (m.contains("睡") || m.contains("休息")) sb.append("休眠模式：月光灯10%；");
         return sb.length() > 0 ? sb.toString() : "默认参数：开灯，亮度50%，色温4000K";
     }
     private int extractBrightness(String r) {
         if (r.contains("80%")) return 80;
         if (r.contains("70%")) return 70;
         if (r.contains("50%")) return 50;
         if (r.contains("30%")) return 30;
         if (r.contains("10%") || r.contains("5%")) return 5;
         return 50;
     }
     private int extractColorTemp(String r) {
         if (r.contains("6000K")) return 60;
         if (r.contains("3000K")) return 30;
         if (r.contains("4000K")) return 40;
         return 50;
     }
 
     public static FunctionToolCallback create() {
         return FunctionToolCallback.builder("agent_reasoning",
                 new AgentReasoningTool())
             .description("水族灯Agent推理工具。处理模糊指令，结合时间/鱼类/场景推理最优灯光参数。")
             .inputType(Request.class).build();
     }
     public static class Request {
         private String message;
         private String timeOfDay;
         private String fishType;
         public String getMessage() { return message; }
         public void setMessage(String v) { this.message = v; }
         public String getTimeOfDay() { return timeOfDay; }
         public void setTimeOfDay(String v) { this.timeOfDay = v; }
         public String getFishType() { return fishType; }
         public void setFishType(String v) { this.fishType = v; }
     }
 }
