package org.happyhai.springai.alibaba.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.memory.redis.BaseRedisChatMemoryRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
 
/**
 * ChatModel+ChatClient+多模型共存+Redis持久化记忆配置类
 */
@Configuration
public class LLMConfig {
    // 模型名称常量统一管理
    private final String DEEPSEEK_MODEL = "deepseek-chat";
    private final String QWEN_MODEL = "qwen-plus";
 
    @Bean(name = "deepseekChatModel")
    public ChatModel deepSeekChatModel() {
        return DashScopeChatModel.builder()
                .dashScopeApi(DashScopeApi.builder()
                        .baseUrl("https://api.deepseek.com/")
                        .apiKey(System.getenv("OPENAI_API_KEY"))
                        .build())
                .defaultOptions(DashScopeChatOptions.builder()
                        .model(DEEPSEEK_MODEL)
                        .temperature(0.7)
                        .build())
                .build();
    }
 
    @Bean(name = "qwenChatModel")
    public ChatModel qwenChatModel() {
        return DashScopeChatModel.builder()
                .dashScopeApi(DashScopeApi.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                        .build())
                .defaultOptions(DashScopeChatOptions.builder()
                        .model(QWEN_MODEL)
                        .temperature(0.7)
                        .build())
                .build();
    }
 
    // ==================== 带Redis记忆的ChatClient 实例注册 ====================
    @Bean(name = "qwenChatClient")
    public ChatClient qwenChatClient(
            @Qualifier("qwenChatModel") ChatModel qwenChatModel,
            ChatMemoryRepository chatMemoryRepository) {
        
        // 1. 配置基于Redis的 ChatMemory
        MessageWindowChatMemory windowChatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(10)
                .build();
 
        // 2. 配置MessageChatMemoryAdvisor
        MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(windowChatMemory)
                .build();
 
        // 3. 构建ChatClient
        return ChatClient.builder(qwenChatModel)
                .defaultOptions(ChatOptions.builder().model(QWEN_MODEL).build())
                .defaultAdvisors(memoryAdvisor)
                .build();
    }
 
    @Bean(name = "deepseekChatClient")
    public ChatClient deepseekChatClient(
            @Qualifier("deepseekChatModel") ChatModel deepSeekChatModel,
            ChatMemoryRepository chatMemoryRepository) {
        
        MessageWindowChatMemory windowChatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(10)
                .build();
 
        return ChatClient.builder(deepSeekChatModel)
                .defaultOptions(ChatOptions.builder().model(DEEPSEEK_MODEL).build())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(windowChatMemory).build())
                .build();
    }
}
