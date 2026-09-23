package org.happyhai.springai.alibaba.mcp.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MCP 服务器启动入口。
 * <p>默认端口 8080,对外暴露 /mcp 端点(Streamable HTTP),
 * 用 SM01 授权服务器签发的 JWT 鉴权(由 mcp-server-security 配置)。</p>
 */
@SpringBootApplication
public class McpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }
}