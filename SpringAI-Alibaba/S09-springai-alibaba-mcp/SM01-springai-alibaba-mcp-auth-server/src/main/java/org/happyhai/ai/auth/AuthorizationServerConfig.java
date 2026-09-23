
package org.happyhai.ai.auth;

import org.springaicommunity.mcp.security.authorizationserver.config.McpAuthorizationServerConfigurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Duration;
import java.util.UUID;

/**
 * MCP 授权服务器安全配置。
 * <p>
 * 激活 mcp-authorization-server 提供的 MCP 规范扩展(动态客户端注册、Resource Indicators 等),
 * 并注册一个 {@code zetlight-mcp-client} 用于 client_credentials / authorization_code 流程。
 */
@Configuration
@EnableWebSecurity
public class AuthorizationServerConfig {

    @Value("${spring.security.oauth2.authorizationserver.client.default-client.registration.client-id:zetlight-mcp-client}")
    private String clientId;

    @Value("${spring.security.oauth2.authorizationserver.client.default-client.registration.client-secret:{noop}change-me-please}")
    private String clientSecret;

    /**
     * 把 yml 配的 default-client 注册到 Spring Authorization Server。
     * 缺这个 bean 的话,client_credentials / authorization_code 流程都拿不到 token(401 invalid_client)。
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository() {
        RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://127.0.0.1:8080/authorize/oauth2/code/authserver")
                .redirectUri("http://localhost:8080/authorize/oauth2/code/authserver")
                .redirectUri("http://localhost:6274/oauth/callback")
                .redirectUri("https://claude.ai/api/mcp/auth_callback")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope("read")
                .scope("write")
                .tokenSettings(TokenSettings.builder()
                        .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .accessTokenTimeToLive(Duration.ofHours(1))
                        .build())
                .build();
        return new InMemoryRegisteredClientRepository(client);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                // 启用 MCP 授权服务器扩展(Dynamic Client Registration、Resource Indicators 等)
                .with(McpAuthorizationServerConfigurer.mcpAuthorizationServer(), Customizer.withDefaults())
                // 启用表单登录,登录用户由 application.yml 中 spring.security.user 配置
                .formLogin(Customizer.withDefaults())
                .build();
    }
}

