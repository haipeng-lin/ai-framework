package org.happyhai.agentscope.middleware.middleware;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.MiddlewareBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Function;
import reactor.core.publisher.Flux;

/**
 * 工具调用入参 / 结果全记录中间件。
 *
 * <p>挂在 {@code onActing} hook：
 * <ul>
 *   <li><b>调用前</b>：把每个待执行工具的 {@code id} / {@code name} / {@code input} 参数打印出来。
 *       这一点很关键 —— 无论下游工具最终是 SUCCESS 还是 ERROR（甚至被拒、被熔断），
 *       入参都已经落地日志里，方便事后复现"我当时到底想让工具做什么"。</li>
 *   <li><b>调用后</b>：识别 {@code TOOL_RESULT_END} 事件，把最终 state 也打一行，
 *       便于把入参和结果对账。</li>
 * </ul>
 *
 * <p>这是一个纯观察型中间件：不修改流、不影响调度，只在日志里留下可追溯的痕迹。
 */
public class ToolInvocationLogMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(ToolInvocationLogMiddleware.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final boolean enabled;

    public ToolInvocationLogMiddleware() {
        this(true);
    }

    public ToolInvocationLogMiddleware(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Flux<AgentEvent> onActing(Agent agent,
                                     RuntimeContext ctx,
                                     ActingInput input,
                                     Function<ActingInput, Flux<AgentEvent>> next) {
        if (!enabled || input == null || input.toolCalls() == null || input.toolCalls().isEmpty()) {
            return next.apply(input);
        }

        String sessionId = ctx == null ? null : ctx.getSessionId();
        String agentName = safeAgentName(agent);

        // 1) 调用前：逐条打印入参。无论下游成功/失败，这里已经留下证据。
        for (ToolUseBlock call : input.toolCalls()) {
            log.info("[tool-invoke] -> 调用工具 agent={} sessionId={} toolCallId={} name={} input={}",
                    agentName,
                    sessionId,
                    call.getId(),
                    call.getName(),
                    toJson(call.getInput()));
        }

        // 2) 调用后：识别 TOOL_RESULT_END，把最终结果也打一行
        return next.apply(input)
                .doOnNext(event -> {
                    if (event == null || event.getType() != AgentEventType.TOOL_RESULT_END) {
                        return;
                    }
                    ToolResultEndEvent e = (ToolResultEndEvent) event;
                    ToolResultState state = e.getState();
                    log.info("[tool-invoke] <- 工具返回 agent={} sessionId={} toolCallId={} name={} state={}",
                            agentName,
                            sessionId,
                            e.getToolCallId(),
                            e.getToolCallName(),
                            state == null ? "UNKNOWN" : state.name());
                });
    }

    private static String safeAgentName(Agent agent) {
        try {
            return agent == null ? "unknown" : agent.getName();
        } catch (Exception ex) {
            return "unavailable";
        }
    }

    private static String toJson(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return "{}";
        }
        try {
            return MAPPER.writeValueAsString(input);
        } catch (JsonProcessingException e) {
            return String.valueOf(input);
        }
    }
}
