package org.happyhai.agentscope.mcp.server.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 网关鉴权 Filter：只对 /mcp 端点生效。
 * Token 既可以从环境变量 MCP_GATEWAY_TOKEN 注入（推荐），
 * 也可以从 agentscope.mcp.token 配置注入，方便本地无 env 调试。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(GatewayAuthFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final String expectedToken;

    public GatewayAuthFilter(@Value("${agentscope.mcp.token}") String configToken) {
        String envToken = System.getenv("MCP_GATEWAY_TOKEN");
        String chosen = (envToken != null && !envToken.isBlank()) ? envToken : configToken;
        if (chosen == null || chosen.isBlank()) {
            log.warn("MCP_GATEWAY_TOKEN not set and agentscope.mcp.token not configured. "
                    + "All /mcp requests will be rejected.");
        }
        this.expectedToken = chosen;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 仅拦截 /mcp 端点，其它端点（如 actuator 等）保持开放
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/mcp");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (expectedToken == null || expectedToken.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "mcp token not configured");
            return;
        }
        String auth = request.getHeader("Authorization");
        boolean ok = auth != null
                && auth.startsWith(BEARER_PREFIX)
                && expectedToken.equals(auth.substring(BEARER_PREFIX.length()));
        if (!ok) {
            log.debug("Rejecting MCP request {} without valid bearer token", request.getRequestURI());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "invalid or missing bearer token");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
