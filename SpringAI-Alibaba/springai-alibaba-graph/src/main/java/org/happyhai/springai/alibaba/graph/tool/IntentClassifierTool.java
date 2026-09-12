 package org.happyhai.springai.alibaba.graph.tool;
 
 import org.happyhai.springai.alibaba.graph.domain.IntentType;
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;
 import org.springframework.ai.tool.function.FunctionToolCallback;
 import org.springframework.ai.chat.model.ToolContext;
 import java.util.function.BiFunction;
 
 /**
  * Router tool used by the entry node to classify user intent.
  * Returns the intent type which drives the conditional edge routing.
  */
 public class IntentClassifierTool implements BiFunction<IntentClassifierTool.Request, ToolContext, String> {
     private static final Logger log = LoggerFactory.getLogger(IntentClassifierTool.class);
 
     @Override
     public String apply(Request req, ToolContext ctx) {
         log.info("[Router] 意图分类 - message: {}", req.message);
         IntentType type = classify(req.message);
         log.info("[Router] 分类结果: {} ({})", type.name(), type.getLabel());
         return String.format(
             "{\"intentType\":\"%s\",\"intentLabel\":\"%s\",\"nextNode\":\"%s\"}",
             type.name(), type.getLabel(), type.name()
         );
     }
 
     IntentType classify(String msg) {
         if (msg == null || msg.isBlank()) return IntentType.CHITCHAT;
         String m = msg.toLowerCase();
         if (isSafetyViolation(m, msg)) return IntentType.SAFETY_VIOLATION;
         if (isTroubleshooting(m)) return IntentType.TROUBLESHOOTING;
         if (isStatusQuery(m)) return IntentType.STATUS_QUERY;
         if (isMemory(m)) return IntentType.MEMORY_MANAGEMENT;
         if (isExplicitControl(m)) return IntentType.EXPLICIT_CONTROL;
         if (isFuzzyControl(m, msg)) return IntentType.FUZZY_CONTROL;
         return IntentType.CHITCHAT;
     }
 
     private boolean isSafetyViolation(String m, String raw) {
         return m.contains("emergency") || m.contains("bypass") || m.contains("强制") ||
                m.contains("覆盖") || m.contains("override") || m.contains("hack") ||
                m.contains("所有设备") || m.contains("all_device");
     }
     private boolean isTroubleshooting(String m) {
         return m.contains("连不上") || m.contains("离线") || m.contains("offline") ||
                m.contains("激活") || m.contains("激活不了") || m.contains("故障") ||
                m.contains("不亮") || m.contains("不工作") || m.contains("mqtt") ||
                m.contains("wifi") && m.contains("不稳定") || m.contains("can't connect") ||
                m.contains("troubleshoot") || m.contains("debug");
     }
     private boolean isStatusQuery(String m) {
         return m.contains("状态") || m.contains("查询") || m.contains("开着吗") ||
                m.contains("当前") || m.contains("亮度多少") || m.contains("色温多少") ||
                m.contains("status") || m.contains("is it on") || m.contains("current state");
     }
     private boolean isMemory(String m) {
         return m.contains("记住") || m.contains("保存") || m.contains("偏好") ||
                m.contains("记忆") || m.contains("常用") || m.contains("记录") ||
                m.contains("recall") || m.contains("remember") || m.contains("save preference");
     }
     private boolean isExplicitControl(String m) {
         return m.matches(".*(开灯|关灯|开|关|打开|关闭|on|off|open|close).*") ||
                m.matches(".*(亮度|亮|暗).*") && m.matches(".*(\\d+|调到|调节).*") ||
                m.matches(".*(色温|颜色温度).*") && m.matches(".*(\\d+|调节).*");
     }
     private boolean isFuzzyControl(String m, String raw) {
         return m.contains("舒服") || m.contains("舒适") || m.contains("好看") ||
                m.contains("鱼") || m.contains("欣赏") || m.contains("拍照") ||
                m.contains("喂食") || m.contains("睡觉") || m.contains("水草") ||
                m.contains("珊瑚") || m.contains("三湖") || m.contains("慈鲷") ||
                m.contains("早上") || m.contains("晚上") || m.contains("白天") ||
                m.contains("模拟") || m.contains("氛围") || m.contains("comfy") ||
                m.contains("fish") || m.contains("photo") || m.contains("coral") ||
                m.contains("ambient");
     }
 
     public static FunctionToolCallback create() {
         return FunctionToolCallback.builder("intent_classifier",
                 new IntentClassifierTool())
             .description("水族灯意图分类工具。分析用户消息，分类为 EXPLICIT_CONTROL/FUZZY_CONTROL/STATUS_QUERY/MEMORY_MANAGEMENT/CHITCHAT/SAFETY_VIOLATION/TROUBLESHOOTING，返回 intentType、intentLabel 和 nextNode")
             .inputType(Request.class).build();
     }
 
     public static class Request {
         private String message;
         public String getMessage() { return message; }
         public void setMessage(String message) { this.message = message; }
     }
 }
