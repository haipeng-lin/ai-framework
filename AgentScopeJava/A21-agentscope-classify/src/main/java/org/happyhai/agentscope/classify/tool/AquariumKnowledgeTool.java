package org.happyhai.agentscope.classify.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 水族知识库（RAG 替身）。
 *
 * <p>真实场景会接向量库或 Elasticsearch；这里用一张常量表先占位，
 * 覆盖"适合草缸"等常见问法的光谱推荐。
 */
@Component
public class AquariumKnowledgeTool {

    private static final Map<String, String> KNOWLEDGE = Map.of(
            "草缸",     "草缸建议：主灯 60-85%，红光 20-35% 促进花青素合成，色温 5000-6500K，光照 8-10h/天。",
            "红蝴蝶",   "红蝴蝶水草偏好强光（80%+）与红光（25-35%），色温 5500K 左右，光照 10h/天。",
            "月光模式", "夜间月光模式：主灯关闭，月光通道 5-20%，色温 2700-3000K。",
            "日出模式", "日出模式：30 分钟内从 0% 缓升到 30%，色温 3000-4500K。",
            "珊瑚",     "珊瑚缸建议：蓝光 80-100%，色温 9000-20000K，光照 9-12h/天。"
    );

    @Tool(
            name = "aquarium_knowledge",
            description = "检索水族养植相关知识，例如适合某种水草/生物的光谱推荐。",
            readOnly = true,
            concurrencySafe = true
    )
    public String search(
            @ToolParam(name = "query",
                       description = "查询关键词，例如 草缸 / 红蝴蝶 / 月光模式",
                       required = true)
                    String query) {
        String normalized = query == null ? "" : query.trim();
        for (var entry : KNOWLEDGE.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "未命中知识库条目，建议结合通用知识回答，并引导用户补充关键词（草缸 / 红蝴蝶 / 珊瑚 等）。";
    }
}
