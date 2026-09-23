package org.happyhai.springai.alibaba.voice.config;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 给 Spring AI 的 RestClient 套两层请求工厂：
 *
 * <pre>
 *   BufferingClientHttpRequestFactory     ← 把请求体缓冲成 byte[]，自动写 Content-Length
 *     └─ HttpComponentsClientHttpRequestFactory  ← 底层用 Apache HttpClient 5，带连接池
 * </pre>
 *
 * <p><b>为什么需要组合：</b>Spring 6.2 里 {@code HttpComponentsClientHttpRequestFactory}
 * 默认走流式（{@code BodyEntity.getContentLength()} 返回 -1），HttpClient 5 因此使用
 * {@code Transfer-Encoding: chunked}。硅基流动网关对 chunked + multipart 兼容性差，会
 * 返回 HTTP 400/500 且无响应体。</p>
 *
 * <p>外层 {@link BufferingClientHttpRequestFactory} 在调用底层 execute 之前把整个请求体
 * 读进内存并写上 {@code Content-Length} 头，HttpClient 5 看到已知长度后会自动改用
 * {@code Content-Length} 而非 chunked。整个缓冲过程只发生一次（外层那一份），底层 HC5
 * 不再二次缓冲。</p>
 *
 * <p>内存占用上限就是 {@code spring.servlet.multipart.max-file-size}，目前配的是 25MB；
 * 并发上传数受连接池（最大 100 连接、单路由 20）约束，峰值缓冲用量是有限可控的。</p>
 *
 * <p>Spring AI 的 OpenAiAutoConfiguration 通过 {@code ObjectProvider<RestClient.Builder>}
 * 拿构造器，所以这个 bean 会被自动复用——chat 接口同样走这条链路。</p>
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        HttpClient httpClient = HttpClients.custom()
                .setConnectionManager(poolingConnectionManager())
                .setDefaultRequestConfig(defaultRequestConfig())
                .disableAutomaticRetries()
                .build();

        HttpComponentsClientHttpRequestFactory hc5Factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        // HC5 默认流式 → chunked，外层套 BufferingClientHttpRequestFactory 强制 Content-Length
        BufferingClientHttpRequestFactory bufferingFactory = new BufferingClientHttpRequestFactory(hc5Factory);

        return RestClient.builder().requestFactory(bufferingFactory);
    }

    @Bean(destroyMethod = "close")
    public PoolingHttpClientConnectionManager poolingConnectionManager() {
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(10))
                .setSocketTimeout(Timeout.ofSeconds(120))
                .setTimeToLive(TimeValue.ofMinutes(5))
                .build();
        return PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(100)
                .setMaxConnPerRoute(20)
                .setDefaultConnectionConfig(connectionConfig)
                .build();
    }

    private RequestConfig defaultRequestConfig() {
        return RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(10))
                .setResponseTimeout(Timeout.ofSeconds(120))
                .build();
    }
}
