 package org.happyhai.springai.alibaba.graph.tool;
 
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;
 import org.springframework.ai.tool.function.FunctionToolCallback;
 import org.springframework.ai.chat.model.ToolContext;
 import java.util.function.BiFunction;
 
 /**
  * Chitchat Rejection Tool - polite refusal for off-topic messages.
  * Used by the chitchat node.
  */
 public class ChitchatRejectionTool implements BiFunction<ChitchatRejectionTool.Request, ToolContext, String> {
     private static final Logger log = LoggerFactory.getLogger(ChitchatRejectionTool.class);
 
     @Override
     public String apply(Request req, ToolContext ctx) {
         log.info("[Chitchat] category={}, message={}", req.category, req.message);
         String response = switch (req.category.toLowerCase()) {
             case "greeting" -> "您好！我是水族灯智能助手。我可以帮助您：\n" +
                 "  • 开灯/关灯：如「打开灯」「关闭照明」\n" +
                 "  • 调节亮度：如「亮度调到50%」\n" +
                 "  • 查询状态：如「灯现在开着吗」\n" +
                 "  • 故障排查：如「灯连不上手机」\n" +
                 "请问有什么可以帮助您的？";
             case "out_of_scope" -> "抱歉，这个问题超出了水族灯的控制范围。\n" +
                 "我支持：开灯/关灯、亮度调节、色温调节、状态查询、记忆管理、故障排查。\n" +
                 "请告诉我您想要控制的设备或需要的帮助。";
             case "small_talk" -> "谢谢！我是水族灯控制助手。\n" +
                 "我可以帮您管理水族灯的开关、亮度、色温等设置，有问题随时问我！";
             default -> "抱歉，我无法回答这个问题。\n" +
                 "请告诉我您想要控制的设备或需要的帮助类型。";
         };
         return String.format(
             "{\"rejected\":true,\"category\":\"%s\"," +
             "\"response\":\"%s\"}",
             req.category,
             response.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"));
     }
 
     public static FunctionToolCallback create() {
         return FunctionToolCallback.builder("chitchat_rejection", new ChitchatRejectionTool())
             .description("水族灯闲聊拒答工具。礼貌拒绝问候/闲聊/越界问题，引导回有效话题。")
             .inputType(Request.class).build();
     }
     public static class Request {
         private String message;
         private String category;
         public String getMessage() { return message; }
         public void setMessage(String v) { this.message = v; }
         public String getCategory() { return category; }
         public void setCategory(String v) { this.category = v; }
     }
 }
