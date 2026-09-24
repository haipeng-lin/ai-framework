package org.happyhai.agentscope.classify.intent;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 意图分类工具，对应设计稿 2.4 节。
 *
 * <p>同时被两种调用方式使用：
 * <ol>
 *   <li>作为 OrchestratorAgent 的 {@code classify_intent} 工具被 LLM 调用（结构化输出）</li>
 *   <li>作为 Java 服务方法 {@link #classify(String)} 直接被路由器调用（无需 LLM 介入）</li>
 * </ol>
 *
 * <p>内部实现是"规则引擎 + LLM 兜底"的混合方案：规则命中耗时 &lt; 10ms，
 * 只有命中不到时才走 DashScope ChatModel 做一次小模型分类。
 */
@Component
public class IntentClassifier {

    private static final Logger log = LoggerFactory.getLogger(IntentClassifier.class);

    private static final String CLASSIFY_SYSTEM = """
            你是水族灯智能助手的意图分类器。
            五个意图枚举：
              CHIT_CHAT         闲聊、问候、情感表达，与水族灯控制无关
              KNOWLEDGE_QA      知识问答：养鱼、水草、灯光光谱原理等
              EXPLICIT_CONTROL  明确控制：参数齐全，可直接执行（例如"主灯60%"、"色温5000K"）
              FUZZY_CONTROL     模糊控制：有控制意图但参数不完整或模糊（例如"亮一点"、"适合草缸"）
              STATUS_QUERY      状态查询：查看当前灯光状态、定时计划等

            同时尽量抽取 slots，并评估 complexity_score（0-5，FUZZY_CONTROL 时尤为有用）。
            仅输出 JSON，不要包含任何额外文字。JSON 形如：
            {"intent":"EXPLICIT_CONTROL","confidence":0.92,"slots":{"channel":"main","brightness":60},"complexity_score":0}
            """;

    private static final List<Pattern> EXPLICIT_CONTROL_PATTERNS = List.of(
            Pattern.compile("(?<channel>主灯|蓝灯|红灯|月光)?\\s*亮度\\s*(?<val>\\d{1,3})\\s*%?"),
            Pattern.compile("(?<channel>主灯|蓝灯|红灯|月光)?\\s*色温\\s*(?<val>\\d{3,5})\\s*K?"),
            Pattern.compile("(?<channel>主灯|蓝灯|红灯|月光)?\\s*调到\\s*(?<val>\\d{1,3})"),
            Pattern.compile("开灯|关灯|关掉|打开|启动|关闭"),
            Pattern.compile("设置.*预设.*\\b(normal|plant|reef|night|sunrise|sunset)\\b")
    );

    private static final List<Pattern> FUZZY_CONTROL_PATTERNS = List.of(
            Pattern.compile("亮一点|暗一点|再亮|再暗|调亮|调暗|亮些|暗些"),
            Pattern.compile("暖一点|暖一些|冷一点|冷一些|再暖|再冷|暖些|冷些"),
            Pattern.compile("适合.*水草|适合.*鱼|适合草缸|夜间模式|日出模式"),
            Pattern.compile("柔和一点|强烈一点|温和一点|温和一些|强烈一些")
    );

    private static final List<Pattern> STATUS_QUERY_PATTERNS = List.of(
            Pattern.compile("当前|现在|此刻|看一下|看看"),
            Pattern.compile("状态|亮度多少|色温多少|温度多少|定时"),
            Pattern.compile("开着没|是开着|是关着|有没有")
    );

    private static final List<Pattern> KNOWLEDGE_PATTERNS = List.of(
            Pattern.compile("什么是|为什么|区别|怎么养|怎么选|原理|怎么回事"),
            Pattern.compile("草缸|水草|红蝴蝶|珊瑚|月光|日出|光谱|Kelvin|K值"),
            Pattern.compile("推荐.*(?:灯|光谱|模式)|哪个.*好")
    );

    private final Model chatModel;
    private final boolean ruleEnabled;

    public IntentClassifier(Model chatModel,
                            @Value("${classify.rule-classifier-enabled:true}") boolean ruleEnabled) {
        this.chatModel = chatModel;
        this.ruleEnabled = ruleEnabled;
    }

    /**
     * 公开 API：路由层调用。
     *
     * @return 分类结果（包含意图、置信度、slots、复杂度评分）
     */
    public IntentResult classify(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return IntentResult.simple(IntentType.CHIT_CHAT, 0.5, Map.of(), "");
        }
        if (ruleEnabled) {
            IntentResult ruleResult = ruleBased(userMessage);
            if (ruleResult != null) {
                log.debug("Rule engine hit: intent={}, confidence={}",
                        ruleResult.intent(), ruleResult.confidence());
                return ruleResult;
            }
        }
        IntentResult llmResult = llmBased(userMessage);
        log.debug("LLM classified: intent={}, confidence={}",
                llmResult.intent(), llmResult.confidence());
        return llmResult;
    }

    /** 对外暴露为 Tool 的入口，与 {@link #classify(String)} 等价，便于在 Agent 上注册。 */
    @Tool(
            name = "classify_intent",
            description = "分析用户输入的意图类型和相关参数。"
                        + "返回 intent/confidence/slots/complexity_score 字段。"
                        + "枚举：CHIT_CHAT / KNOWLEDGE_QA / EXPLICIT_CONTROL / FUZZY_CONTROL / STATUS_QUERY",
            readOnly = true,
            concurrencySafe = true
    )
    public IntentResult classifyIntent(
            @ToolParam(name = "user_message",
                       description = "用户原始输入文本",
                       required = true)
                    String userMessage) {
        return classify(userMessage);
    }

    private IntentResult ruleBased(String message) {
        Map<String, Object> slots = new LinkedHashMap<>();
        if (matchesAny(message, EXPLICIT_CONTROL_PATTERNS, slots)) {
            return IntentResult.simple(IntentType.EXPLICIT_CONTROL, 0.9, slots, message);
        }
        if (matchesAny(message, FUZZY_CONTROL_PATTERNS, slots)) {
            int complexity = estimateComplexity(message);
            return new IntentResult(IntentType.FUZZY_CONTROL, 0.85, slots, message, complexity);
        }
        if (matchesAny(message, STATUS_QUERY_PATTERNS, slots)) {
            return IntentResult.simple(IntentType.STATUS_QUERY, 0.85, slots, message);
        }
        if (matchesAny(message, KNOWLEDGE_PATTERNS, slots)) {
            return IntentResult.simple(IntentType.KNOWLEDGE_QA, 0.8, slots, message);
        }
        return null;
    }

    private static boolean matchesAny(String message, List<Pattern> patterns, Map<String, Object> slots) {
        for (Pattern p : patterns) {
            Matcher m = p.matcher(message);
            if (m.find()) {
                if (m.groupCount() > 0) {
                    for (int i = 1; i <= m.groupCount(); i++) {
                        String name = patternGroupName(p, i);
                        String val = m.group(i);
                        if (name != null && val != null) {
                            slots.put(name, val);
                        }
                    }
                }
                return true;
            }
        }
        return false;
    }

    private static String patternGroupName(Pattern p, int groupIndex) {
        String[] groups = {"channel", "val"};
        if (groupIndex >= 1 && groupIndex <= groups.length) {
            return groups[groupIndex - 1];
        }
        return null;
    }

    private static int estimateComplexity(String message) {
        int score = 1;
        String[] tokens = {"主灯", "蓝灯", "红灯", "月光", "色温", "定时", "方案"};
        for (String t : tokens) {
            if (message.contains(t)) score++;
        }
        return Math.min(score, 5);
    }

    /**
     * 通过 DashScope 小模型分类。系统提示要求仅返回 JSON。
     * 真实生产中可以换成 DashScopeChatModel 的 tools/function-calling，
     * 这里为了依赖最简，使用文本约束 + 简单 JSON 解析。
     */
    private IntentResult llmBased(String userMessage) {
        try {
            Msg systemMsg = Msg.builder()
                    .role(MsgRole.SYSTEM)
                    .textContent(CLASSIFY_SYSTEM)
                    .build();
            Msg userMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .textContent(userMessage)
                    .build();
            GenerateOptions opts = GenerateOptions.builder()
                    .temperature(0.0)
                    .build();
            StringBuilder buffer = new StringBuilder();
            chatModel.stream(List.of(systemMsg, userMsg), List.of(), opts)
                    .timeout(Duration.ofSeconds(15))
                    .doOnNext(resp -> appendText(buffer, resp))
                    .doOnError(err -> log.warn("LLM classifier stream error: {}", err.getMessage()))
                    .blockLast();
            return parseLlmJson(buffer.toString(), userMessage);
        } catch (Exception e) {
            log.warn("LLM classifier failed, fallback to CHIT_CHAT: {}", e.getMessage());
            return IntentResult.simple(IntentType.CHIT_CHAT, 0.4,
                    Map.of("fallback", true), userMessage);
        }
    }

    private static void appendText(StringBuilder buffer, ChatResponse resp) {
        if (resp == null) {
            return;
        }
        for (ContentBlock block : resp.getContent()) {
            if (block instanceof TextBlock tb && tb.getText() != null) {
                buffer.append(tb.getText());
            }
        }
    }

    /** 极简 JSON 解析，仅支持本工具生成的固定 schema。 */
    static IntentResult parseLlmJson(String content, String rawUtterance) {
        if (content == null || content.isEmpty()) {
            return IntentResult.simple(IntentType.CHIT_CHAT, 0.4,
                    Map.of("parse_error", true), rawUtterance);
        }
        String trimmed = content.trim();
        if (!trimmed.startsWith("{")) {
            int braceStart = trimmed.indexOf('{');
            int braceEnd = trimmed.lastIndexOf('}');
            if (braceStart >= 0 && braceEnd > braceStart) {
                trimmed = trimmed.substring(braceStart, braceEnd + 1);
            }
        }
        try {
            IntentType intent = readEnumField(trimmed, "intent", IntentType.CHIT_CHAT);
            double confidence = readNumberField(trimmed, "confidence", 0.6);
            Map<String, Object> slots = readSlotsField(trimmed);
            int complexity = (int) readNumberField(trimmed, "complexity_score", 0);
            return new IntentResult(intent, confidence, slots, rawUtterance, complexity);
        } catch (Exception e) {
            log.debug("LLM JSON parse failed: {}", e.getMessage());
            return IntentResult.simple(IntentType.CHIT_CHAT, 0.4,
                    Map.of("parse_error", true), rawUtterance);
        }
    }

    private static IntentType readEnumField(String json, String field, IntentType fallback) {
        Pattern p = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([A-Z_]+)\"");
        Matcher m = p.matcher(json);
        if (m.find()) {
            try {
                return IntentType.valueOf(m.group(1));
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        return fallback;
    }

    private static double readNumberField(String json, String field, double fallback) {
        Pattern p = Pattern.compile("\"" + field + "\"\\s*:\\s*([0-9.]+)");
        Matcher m = p.matcher(json);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1));
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return fallback;
    }

    private static Map<String, Object> readSlotsField(String json) {
        Pattern p = Pattern.compile("\"slots\"\\s*:\\s*\\{([^}]*)\\}");
        Matcher m = p.matcher(json);
        Map<String, Object> slots = new LinkedHashMap<>();
        if (m.find()) {
            String body = m.group(1);
            Pattern kv = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\"[^\"]*\"|[0-9.]+)");
            Matcher mm = kv.matcher(body);
            while (mm.find()) {
                String k = mm.group(1);
                String v = mm.group(2);
                if (v.startsWith("\"")) {
                    slots.put(k, v.substring(1, v.length() - 1));
                } else {
                    try {
                        slots.put(k, Double.parseDouble(v));
                    } catch (NumberFormatException e) {
                        slots.put(k, v);
                    }
                }
            }
        }
        return slots;
    }

    // 保留 array 引用以保持可读（不直接使用）
    @SuppressWarnings("unused")
    private static final List<Object> UNUSED = new ArrayList<>();
}
