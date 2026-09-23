package org.happyhai.springai.alibaba.mcp.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MCP 测试客户端启动入口。
 * <p>
 * 启动后由 {@link McpClientTestRunner} 自动执行测试:
 * <ol>
 *   <li>从 SM01 (localhost:9000) 用 client_credentials 拿 access token</li>
 *   <li>连接 SM02 (localhost:8080/mcp),列出可用工具</li>
 *   <li>调用 echo / add / listModes / controlLight 等工具,验证链路</li>
 * </ol>
 * </p>
 */
@SpringBootApplication
public class McpClientTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpClientTestApplication.class, args);
    }
}