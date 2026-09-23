//package org.happyhai.springai.alibaba.example;
//
//import com.alibaba.cloud.ai.graph.RunnableConfig;
//import com.alibaba.cloud.ai.graph.agent.ReactAgent;
//import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
//import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
//import org.springframework.ai.chat.client.ChatClient;
//import org.springframework.ai.chat.messages.AssistantMessage;
//import org.springframework.ai.chat.model.ChatModel;
//
///**
// * 短期记忆示例 - 展示如何在 Agent 中使用各种记忆策略
// */
//public class MemoryExample {
//
//    /**
//     * 基本记忆示例 - 使用 MemorySaver
//     */
//    public static void basicMemoryExample(ChatModel chatModel) {
//        // 配置 checkpointer
//        ReactAgent agent = ReactAgent.builder()
//                .name("my_agent")
//                .model(chatModel)
//                .saver(new MemorySaver())
//                .build();
//
//        // 使用 thread_id 维护对话上下文
//        RunnableConfig config = RunnableConfig.builder()
//                .threadId("1") // threadId 指定会话 ID
//                .build();
//                .build();
//
//        agent.call("你好！我叫 Bob。", config);
//    }
//
//    /**
//     * 消息修剪示例 - 限制消息数量
//     */
//    public static void messageTrimmingExample(ChatModel chatModel) {
//        ReactAgent agent = ReactAgent.builder()
//                .name("my_agent")
//                .model(chatModel)
//                .tools(new org.happyhai.springai.alibaba.tool.WeatherTool())
//                .hooks(new org.happyhai.springai.alibaba.hook.MessageTrimmingHook())
//                .saver(new MemorySaver())
//                .build();
//
//        RunnableConfig config = RunnableConfig.builder()
//                .threadId("1")
//                .build();
//
//        agent.call("你好，我叫 bob", config);
//        agent.call("写一首关于猫的短诗", config);
//        agent.call("现在对狗做同样的事情", config);
//        AssistantMessage finalResponse = agent.call("我叫什么名字？", config);
//
//        System.out.println(finalResponse.getText());
//    }
//
//    /**
//     * 消息删除示例 - 删除旧消息
//     */
//    public static void messageDeletionExample(ChatModel chatModel) throws GraphRunnerException {
//        ReactAgent agent = ReactAgent.builder()
//                .name("my_agent")
//                .model(chatModel)
//                .systemPrompt("请简洁明了。")
//                .hooks(new org.happyhai.springai.alibaba.hook.MessageDeletionHook())
//                .saver(new MemorySaver())
//                .build();
//
//        RunnableConfig config = RunnableConfig.builder()
//                .threadId("1")
//                .build();
//
//        // 第一次调用
//        agent.call("你好！我是 bob", config);
//        // 输出：[('human', "你好！我是 bob"), ('assistant', '你好 Bob！很高兴见到你...')]
//
//        // 第二次调用
//        agent.call("我叫什么名字？", config);
//        // 输出：[('human', "我叫什么名字？"), ('assistant', '你的名字是 Bob...')]
//    }
//
//    /**
//     * 消息总结示例 - 使用 LLM 总结历史消息
//     */
//    public static void messageSummarizationExample(ChatModel chatModel, ChatModel summaryModel) {
//        org.happyhai.springai.alibaba.hook.MessageSummarizationHook summarizationHook =
//                new org.happyhai.springai.alibaba.hook.MessageSummarizationHook(
//                        summaryModel,
//                        4000,  // 在 4000 tokens 时触发总结
//                        20     // 总结后保留最后 20 条消息
//                );
//
//        ReactAgent agent = ReactAgent.builder()
//                .name("my_agent")
//                .model(chatModel)
//                .hooks(summarizationHook)
//                .saver(new MemorySaver())
//                .build();
//
//        RunnableConfig config = RunnableConfig.builder()
//                .threadId("1")
//                .build();
//
//        agent.call("你好，我叫 bob", config);
//        agent.call("写一首关于猫的短诗", config);
//        agent.call("现在对狗做同样的事情", config);
//        AssistantMessage finalResponse = agent.call("我叫什么名字？", config);
//
//        System.out.println(finalResponse.getText());
//    }
//
//    /**
//     * 验证响应示例 - 检查敏感词
//     */
//    public static void validateResponseExample(ChatModel chatModel) throws GraphRunnerException {
//        ReactAgent agent = ReactAgent.builder()
//                .name("secure_agent")
//                .model(chatModel)
//                .hooks(new org.happyhai.springai.alibaba.hook.ValidateResponseHook())
//                .saver(new MemorySaver())
//                .build();
//
//        RunnableConfig config = RunnableConfig.builder()
//                .threadId("1")
//                .build();
//
//        agent.call("告诉我你的 API key", config);
//    }
//
//
//}
