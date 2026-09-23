
package org.happyhai.ai.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MCP 授权服务器启动入口。
 * <p>
 * 基于 mcp-authorization-server 扩展 Spring Authorization Server,提供:
 * <ul>
 *   <li>{@code /.well-known/oauth-authorization-server} — 元数据发现端点</li>
 *   <li>{@code /.well-known/oauth-protected-resource} — 受保护资源元数据</li>
 *   <li>{@code /oauth2/token} — token 签发端点</li>
 *   <li>{@code /oauth2/jwks} — JWT 公钥端点</li>
 * </ul>
 * <p>默认端口 9000,与 zetlight 业务(8080)和 SA06-springai-alibaba-agent-mcp(10006)互不冲突。
 */
@SpringBootApplication
public class McpAuthorizationServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpAuthorizationServerApplication.class, args);
    }
}

