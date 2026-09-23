package org.happyhai.springai.alibaba.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfig {

    @Bean
    public DashScopeApi ragDashScopeApi() {
        return DashScopeApi.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .build();
    }

    @Bean
    public EmbeddingModel dashScopeEmbeddingModel(DashScopeApi ragDashScopeApi) {
        return DashScopeEmbeddingModel.builder()
                .dashScopeApi(ragDashScopeApi)
                .defaultOptions(DashScopeEmbeddingOptions.builder()
                        .model("text-embedding-v3")
                        .build())
                .build();
    }

    @Bean
    public MilvusServiceClient milvusServiceClient() {
        return new MilvusServiceClient(ConnectParam.newBuilder()
                .withAuthorization("root", "Milvus")
                .withUri("http://8.155.33.36:19530")
                .build());
    }

    @Bean
    public ChatClient.Builder dashScopeChatClientBuilder(DashScopeChatModel dashScopeChatModel) {
        return ChatClient.builder(dashScopeChatModel);
    }

    @Bean
    public VectorStore milvusVectorStore(
            MilvusServiceClient milvusServiceClient,
            @Qualifier("dashScopeEmbeddingModel") EmbeddingModel embeddingModel) {
        return MilvusVectorStore.builder(milvusServiceClient, embeddingModel)
                .collectionName("rag_demo_collection")
                .databaseName("default")
                .indexType(IndexType.IVF_FLAT)
                .metricType(MetricType.COSINE)
                .batchingStrategy(new TokenCountBatchingStrategy())
                .initializeSchema(true)
                .build();
    }
}
