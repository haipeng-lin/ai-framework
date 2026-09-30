package org.happyhai.agentscope.tool.tool;

import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionDecision;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WebSearchTool extends ToolBase {


    private static final String FIRECRAWL_ENDPOINT = "https://api.firecrawl.dev/v1/search";

    private final WebClient webClient;

    public WebSearchTool() {
        super(
                ToolBase.builder()
                        .name("WebSearch")
                        .description("在网络上搜索给定查询词的相关信息。")
                        .inputSchema(Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "query", Map.of(
                                                "type", "string",
                                                "description", "搜索查询词。")),
                                "required", List.of("query")))
                        .readOnly(true)
                        .concurrencySafe(true));
        // Firecrawl 免鉴权模式，无需任何 header，直接建 WebClient
        this.webClient = WebClient.builder()
                .baseUrl(FIRECRAWL_ENDPOINT)
                .build();
    }

    @Override
    public Mono<PermissionDecision> checkPermissions(
            Map<String, Object> toolInput, PermissionContextState context) {
        return Mono.just(PermissionDecision.allow("网络搜索是只读操作。"));
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        String query = (String) param.getInput().get("query");
        return doSearchAsync(query)
                .map(text ->
                        ToolResultBlock.builder()
                                .id(param.getToolUseBlock().getId())
                                .name(getName())
                                .output(List.of(TextBlock.builder().text(text).build()))
                                .build());
    }

    /**
     * 调用 Firecrawl Search API，返回结构化的搜索结果文本。
     */
    private Mono<String> doSearchAsync(String query) {
        // 注意：Firecrawl 的 search 接口是 POST 请求，body 传 JSON 参数
        return webClient.post()
                .uri(uriBuilder -> uriBuilder.path("/").build())  // baseUrl 已含 /v1/search
                .bodyValue(Map.of(
                        "query", query,
                        "limit", 5,                               // 每次搜5条，控制 token 消耗
                        "lang", "zh-CN",                          // 中文结果
                        "country", "cn",                          // 国内优先
                        "scrapeOptions", Map.of(
                                "formats", List.of("markdown"),   // 返回 Markdown 正文
                                "onlyMainContent", true,          // 只取正文，去掉导航/广告
                                "removeBase64Images", true        // 去掉 base64 图片，省 token
                        )
                ))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::formatResults)
                .onErrorResume(e -> Mono.just("搜索失败：" + e.getMessage()));
    }

    /**
     * 把 Firecrawl 返回的 JSON 整理成 LLM 友好的纯文本。
     */
    private String formatResults(JsonNode root) {
        JsonNode data = root.path("data");
        if (!data.isArray() || data.isEmpty()) {
            return "未找到相关搜索结果。";
        }
        return java.util.stream.StreamSupport
                .stream(data.spliterator(), false)
                .map(node -> String.format("【%s】%n%s%n链接：%s",
                        node.path("title").asText(""),
                        node.path("markdown").asText("")   // Firecrawl 返回的是 markdown 字段
                                .replaceAll("\\n{3,}", "\n\n"),  // 压缩多余空行
                        node.path("url").asText("")))
                .collect(Collectors.joining("\n\n---\n\n"));
    }
}