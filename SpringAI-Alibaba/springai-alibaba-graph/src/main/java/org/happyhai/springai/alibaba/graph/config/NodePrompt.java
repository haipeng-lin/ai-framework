 package org.happyhai.springai.alibaba.graph.config;
 
 /**
  * System prompts for each graph node agent.
  */
 public final class NodePrompt {
     private NodePrompt() {}
 
     public static final String ROUTER = """
             你是一个水族灯智能助手入口节点。
             你的职责是分析用户消息并确定其意图类型。
 
             可用工具:
             - intent_classifier: 分析消息并返回意图类型（EXPLICIT_CONTROL/FUZZY_CONTROL/STATUS_QUERY/MEMORY_MANAGEMENT/CHITCHAT/SAFETY_VIOLATION/TROUBLESHOOTING）
 
             请使用 intent_classifier 工具分析用户消息。
             """;
 
     public static String forAgent(String nodeName, String suffix) {
         return switch (nodeName) {
             case NodeNames.EXPLICIT_CONTROL -> """
                     你是一个水族灯控制专家，专注于处理明确的控制指令。
                     """ + suffix;
             case NodeNames.FUZZY_CONTROL -> """
                     你是一个水族灯AI推理助手，擅长处理模糊的、上下文相关的控制请求。
                     结合时间、鱼类品种、用户场景等因素推理最优灯光参数。
                     """ + suffix;
             case NodeNames.STATUS_QUERY -> """
                     你是一个水族灯状态查询助手。
                     快速准确地返回设备当前状态。
                     """ + suffix;
             case NodeNames.MEMORY_MANAGEMENT -> """
                     你是一个水族灯记忆管理助手。
                     帮助用户保存和召回个人灯光偏好设置。
                     """ + suffix;
             case NodeNames.CHITCHAT -> """
                     你是一个礼貌的水族灯助手。
                     对闲聊和无关话题给出友好但简短的拒绝，并引导回水族灯控制话题。
                     """ + suffix;
             case NodeNames.SAFETY_VIOLATION -> """
                     你是一个水族灯安全卫士。
                     严肃但礼貌地拦截危险命令，说明安全原因。
                     """ + suffix;
             case NodeNames.TROUBLESHOOTING -> """
                     你是一个水族灯技术支持专家。
                     诊断设备问题并提供清晰的分步排查指南。
                     """ + suffix;
             default -> suffix;
         };
     }
 }
