
package org.happyhai.springai.alibaba.config;

import org.springaicommunity.mcp.security.client.sync.AuthenticationMcpTransportContextProvider;
import org.springaicommunity.mcp.security.client.sync.oauth2.http.client.OAuth2ClientCredentialsSyncHttpRequestCustomizer;
import org.springframework.ai.mcp.customizer.McpSyncClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * MCP 客户端 OAuth2 鉴权配置(client_credentials 流程)。
 * <p>
 * 与 zetlight 服务端的 {@code McpSecurityConfig} 对应:服务端用 {@code McpServerOAuth2Configurer}
 * 校验 JWT,客户端用 client_credentials 自动从授权服务器获取 access token,并通过
 * {@link AuthenticationMcpTransportContextProvider} 把 token 注入到每个 MCP 请求头。
 *
 * <p>本服务是 Agent,对外不要求鉴权(放行所有请求),MCP 端点 /mcp 由服务端校验。
 * 这里的 SecurityFilterChain 仅用于启用 OAuth2 Client 能力。</p>
 */
@Configuration
@EnableWebSecurity
public class McpClientSecurityConfig {

    /**
     * OAuth2 client registration 名称,必须与 application.yml 中
     * spring.security.oauth2.client.registration.authserver-client-credentials 一致。
     */
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
     * client_credentials 流程的请求定制器:每次 MCP 请求时,会用
     * {@link OAuth2AuthorizedClientManager} 获取/刷新 token 并附加到请求上。
     * <p>注:OAuth2ClientCredentialsSyncHttpRequestCustomizer 要求的是具体类型
     * {@link AuthorizedClientServiceOAuth2AuthorizedClientManager},
     * Spring Boot 引入 {@code spring-boot-starter-oauth2-client} 时会自动配置这个 bean。</p>
     */
    @Bean
    public OAuth2ClientCredentialsSyncHttpRequestCustomizer oAuth2ClientCredentialsRequestCustomizer(
            AuthorizedClientServiceOAuth2AuthorizedClientManager clientManager) {
        return new OAuth2ClientCredentialsSyncHttpRequestCustomizer(clientManager, CLIENT_REGISTRATION_ID);
    }
}
