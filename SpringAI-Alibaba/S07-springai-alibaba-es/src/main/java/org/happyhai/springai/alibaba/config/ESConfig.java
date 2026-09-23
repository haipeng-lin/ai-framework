package org.happyhai.springai.alibaba.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Base64;


@Configuration
public class ESConfig {

    @Value("${spring.elasticsearch.uris}")
    private String elasticsearchUris;

    @Value("${spring.elasticsearch.username:}")
    private String elasticsearchUsername;

    @Value("${spring.elasticsearch.password:}")
    private String elasticsearchPassword;

    @Bean
    public DashScopeApi esDashScopeApi() {
        return DashScopeApi.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .build();
    }

    @Bean
    public EmbeddingModel dashScopeEmbeddingModel(DashScopeApi esDashScopeApi) {
        return DashScopeEmbeddingModel.builder()
                .dashScopeApi(esDashScopeApi)
                .defaultOptions(DashScopeEmbeddingOptions.builder()
                        .model("text-embedding-v3")
                        .build())
                .build();
    }

    @Bean
    public RestClient restClient() throws Exception {
        // 解析 URI
        String host = elasticsearchUris.replace("https://", "").replace("http://", "").replace("/", "");
        int port = 9200;
        boolean isHttps = elasticsearchUris.startsWith("https");

        if (host.contains(":")) {
            String[] parts = host.split(":");
            host = parts[0];
            port = Integer.parseInt(parts[1]);
        }

        RestClientBuilder builder = RestClient.builder(new HttpHost(host, port, isHttps ? "https" : "http"));

        // 配置 Basic Auth
        if (elasticsearchUsername != null && !elasticsearchUsername.isEmpty()) {
            String auth = elasticsearchUsername + ":" + elasticsearchPassword;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            builder.setDefaultHeaders(new Header[]{
                    new BasicHeader("Authorization", "Basic " + encodedAuth)
            });
        }

        // HTTPS 配置（跳过 SSL 证书验证，用于自签名证书）
        if (isHttps) {
            SSLContext sslContext = createIgnoreValidationSSLContext();
            builder.setHttpClientConfigCallback(httpClientBuilder -> {
                httpClientBuilder.setSSLContext(sslContext);
                httpClientBuilder.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
                return httpClientBuilder;
            });
        }

        return builder.build();
    }

    /**
     * Elasticsearch Java Client
     * 用于高级查询和混合检索
     */
    @Bean
    public ElasticsearchClient elasticsearchClient(RestClient restClient) {
        ElasticsearchTransport transport = new RestClientTransport(
                restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }

    private SSLContext createIgnoreValidationSSLContext() throws NoSuchAlgorithmException, KeyManagementException {
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
        };
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        return sslContext;
    }

    @Bean
    public ChatClient.Builder dashScopeChatClientBuilder(DashScopeChatModel dashScopeChatModel) {
        return ChatClient.builder(dashScopeChatModel);
    }

    @Bean
    public ChatClient dashScopeChatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
