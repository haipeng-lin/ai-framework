package org.happyhai.springai.alibaba.mcp.client.config;

import org.springaicommunity.mcp.security.client.sync.AuthenticationMcpTransportContextProvider;
import org.springaicommunity.mcp.security.client.sync.oauth2.http.client.OAuth2ClientCredentialsSyncHttpRequestCustomizer;
import org.springframework.ai.mcp.customizer.McpSyncClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;

/**
 * MCP 客户端 OAuth2 鉴权配置(client_credentials 流程)。
 * <p>
 * 与 SA06/SA07 的 McpClientSecurityConfig 走同一条接入路径,只是连的是本仓库的 SM02,
 * 用来端到端验证 SM01 → SM03 → SM02 的链路。
 * </p>
 *
 * <p>注意:Spring Boot 3.x 的 {@code spring-boot-starter-oauth2-client} 不会自动配置
 * {@link AuthorizedClientServiceOAuth2AuthorizedClientManager} 这个具体类型的 Bean,
 * 只在某些条件下配置 {@code OAuth2AuthorizedClientManager} 接口。本类显式声明这个 Bean,
 * 以保证 mcp-client-security 的 {@link OAuth2ClientCredentialsSyncHttpRequestCustomizer}
 * 能注入到它。</p>
 */
@Configuration
@EnableWebSecurity
public class McpClientSecurityConfig {

    private static final String CLIENT_REGISTRATION_ID = "authserver-client-credentials";

    /**
     * 启用 OAuth2 客户端能力。本服务对外不要求鉴权,放行所有请求。
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .oauth2Client(Customizer.withDefaults())
                .build();
    }

    /**
     * 为每个 MCP 同步客户端设置传输上下文提供者,这样 OAuth2 token 才能被注入到
     * 每次 MCP 请求的 Authorization 头里。
     */
    @Bean
    public McpSyncClientCustomizer syncClientCustomizer() {
        return (name, syncSpec) -> syncSpec.transportContextProvider(
                new AuthenticationMcpTransportContextProvider());
    }

    /**
     * 显式声明 client_credentials 用的 client manager。
     * <p>
     * Spring Boot 3.x 默认只配置 {@code OAuth2AuthorizedClientManager} 接口 Bean,
     * mcp-client-security 的 {@link OAuth2ClientCredentialsSyncHttpRequestCustomizer}
     * 要求具体类型 {@link AuthorizedClientServiceOAuth2AuthorizedClientManager},
     * 所以这里手动建一个。
     * </p>
     */
    @Bean
    public AuthorizedClientServiceOAuth2AuthorizedClientManager authorizedClientServiceOAuth2AuthorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService) {
        OAuth2AuthorizedClientProvider provider = OAuth2AuthorizedClientProviderBuilder.builder()
                .authorizationCode()
                .clientCredentials()
                .refreshToken()
                .build();
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                        clientRegistrationRepository, authorizedClientService);
        manager.setAuthorizedClientProvider(provider);
        return manager;
    }

    /**
     * client_credentials 流程的请求定制器:每次 MCP 请求时,会用
     * {@link org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager}
     * 获取/刷新 token 并附加到请求上。
     */
    @Bean
    public OAuth2ClientCredentialsSyncHttpRequestCustomizer oAuth2ClientCredentialsRequestCustomizer(
            AuthorizedClientServiceOAuth2AuthorizedClientManager clientManager) {
        return new OAuth2ClientCredentialsSyncHttpRequestCustomizer(clientManager, CLIENT_REGISTRATION_ID);
    }
}