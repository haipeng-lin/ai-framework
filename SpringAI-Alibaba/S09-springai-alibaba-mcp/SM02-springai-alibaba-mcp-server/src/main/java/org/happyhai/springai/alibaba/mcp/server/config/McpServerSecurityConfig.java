package org.happyhai.springai.alibaba.mcp.server.config;

import org.springaicommunity.mcp.security.server.config.McpServerOAuth2Configurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * MCP 服务端 OAuth2 资源服务器配置。
 * <p>
 * 用 mcp-server-security 提供的 {@link McpServerOAuth2Configurer} 把
 * {@code /mcp} 端点改成 OAuth2 资源服务器,JWT 必须由 SM01 的
 * {@code issuer-uri} 签发。
 * </p>
 *
 * <p>类路径纠正记录:早期版本误用了 {@code org.springaicommunity.mcp.server.security},
 * 实际包名是 {@code org.springaicommunity.mcp.security.server.config}。</p>
 */
@Configuration
@EnableWebSecurity
public class McpServerSecurityConfig {

    /**
     * 授权服务器的 issuer URI,从 application.yml 的
     * spring.security.oauth2.resourceserver.jwt.issuer-uri 读取,
     * 默认 http://localhost:9000 (即 SM01)。
     */
    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:http://localhost:9000}")
    private String issuerUri;

    /**
     * 配置 Spring Security:
     * <ul>
     *   <li>actuator / /error 走 permitAll,方便本地观察启动状态</li>
     *   <li>其它路径全部要求已认证</li>
     *   <li>把 MCP OAuth2 配置器挂到链上 —— 它会用 issuerUri 去拉 JWKS,
     *       校验每个 MCP 请求头里的 Bearer JWT,同时按 MCP 规范暴露
     *       {@code /.well-known/oauth-protected-resource} 元数据</li>
     * </ul>
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**", "/", "/error").permitAll()
                        .anyRequest().authenticated()
                )
                .with(McpServerOAuth2Configurer.mcpServerOAuth2(), cfg ->
                        cfg.authorizationServer(issuerUri)
                )
                .build();
    }
}