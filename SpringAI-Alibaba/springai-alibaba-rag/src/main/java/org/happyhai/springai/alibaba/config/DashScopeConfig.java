package org.happyhai.springai.alibaba.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DashScopeConfig {

    @Bean
    public DashScopeApi dashScopeApi() {
        return DashScopeApi.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .build();
    }

    @Bean
    @Qualifier("dashScopeChatModel")
    public DashScopeChatModel dashScopeChatModel(DashScopeApi dashScopeApi) {
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .model("qwen-plus")
                        .temperature(0.7)
                        .maxToken(2000)
                        .topP(0.9)
                        .build())
                .build();
    }

    @Bean
    public ChatClient dashScopeChatClient(@Qualifier("dashScopeChatModel") DashScopeChatModel dashScopeChatModel) {
        return ChatClient.builder(dashScopeChatModel).build();
    }
}
