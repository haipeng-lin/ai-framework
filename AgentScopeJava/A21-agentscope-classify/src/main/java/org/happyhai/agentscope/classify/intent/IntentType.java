package org.happyhai.agentscope.classify.intent;

/**
 * 五种意图定义。
 *
 * <p>参考原始设计稿第 2.1 节：
 * <pre>
 *   CHIT_CHAT        闲聊：打招呼、情感交流、无关话题
 *   KNOWLEDGE_QA     知识问答：养鱼知识、水草养护、灯光光谱原理等
 *   EXPLICIT_CONTROL 明确控制：参数齐全，可直接执行
 *   FUZZY_CONTROL    模糊控制：意图是控制但参数不完整/模糊
 *   STATUS_QUERY     状态查询：查看当前灯光状态、定时计划等
 * </pre>
 */
public enum IntentType {
    CHIT_CHAT,
    KNOWLEDGE_QA,
    EXPLICIT_CONTROL,
    FUZZY_CONTROL,
    STATUS_QUERY
}
