 package org.happyhai.springai.alibaba.graph.config;
 
 import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
 import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
 import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;
 import org.springframework.ai.chat.model.ChatModel;
 import org.springframework.beans.factory.annotation.Value;
 import org.springframework.context.annotation.Bean;
 import org.springframework.context.annotation.Configuration;
 
 @Configuration
 public class ChatModelConfig {
     private static final Logger log = LoggerFactory.getLogger(ChatModelConfig.class);
 
     @Bean
     public DashScopeApi dashScopeApi() {
         String apiKey = System.getenv("DASHSCOPE_API_KEY");
         if (apiKey == null || apiKey.isBlank()) {
             log.warn("DASHSCOPE_API_KEY 环境变量未设置");
         }
         return DashScopeApi.builder()
                 .apiKey(apiKey != null ? apiKey : "")
                 .build();
     }
 
     @Bean
     public ChatModel chatModel(DashScopeApi api,
                                @Value("${graph.model:qwen-plus}") String modelName) {
         return DashScopeChatModel.builder()
                 .dashScopeApi(api)
                 .defaultOptions(DashScopeChatOptions.builder()
                         .model(modelName)
                         .build())
                 .build();
     }
 }
