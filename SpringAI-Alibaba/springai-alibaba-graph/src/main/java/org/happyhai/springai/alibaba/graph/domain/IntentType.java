 package org.happyhai.springai.alibaba.graph.domain;
 
 /**
  * Intent classification types for aquarium light control.
  * These correspond to the 7 specialized nodes in the graph.
  */
 public enum IntentType {
     EXPLICIT_CONTROL("明确控制指令", 1),
     FUZZY_CONTROL("模糊控制指令", 2),
     STATUS_QUERY("状态查询", 3),
     MEMORY_MANAGEMENT("记忆管理", 4),
     CHITCHAT("越界/闲聊", 5),
     SAFETY_VIOLATION("安全违规", 6),
     TROUBLESHOOTING("故障排查/技术支持", 7);
 
     private final String label;
     private final int priority;
 
     IntentType(String label, int priority) {
         this.label = label;
         this.priority = priority;
     }
 
     public String getLabel() { return label; }
     public int getPriority() { return priority; }
 }
