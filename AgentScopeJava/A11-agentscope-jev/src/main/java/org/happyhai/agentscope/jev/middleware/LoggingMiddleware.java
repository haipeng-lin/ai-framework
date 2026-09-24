package org.happyhai.agentscope.jev.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.middleware.ReasoningInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * 演示同时实现 5 个 hook：onAgent / onReasoning / onActing / onModelCall / onSystemPrompt。
 *
 * <p>该中间件覆盖最外层（order = 10），所以能在每个事件流的最外层观察到事件。
 * 其余未实现的 hook（onActing）由 MiddlewareBase 的默认实现直接转给 next。
 */
public class LoggingMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(LoggingMiddleware.class);

    @Override
    public int order() {
        return 10;
    }

    @Override
    public Flux<AgentEvent> onAgent(Agent agent,
                                    RuntimeContext ctx,
                                    AgentInput input,
                                    Function<AgentInput, Flux<AgentEvent>> next) {
        log.info("[mw:logging] -> onAgent  agent={} sessionId={} userId={} msgs={}",
                agent.getName(), ctx.getSessionId(), ctx.getUserId(), input.msgs().size());
        long t0 = System.nanoTime();
        return next.apply(input)
                .doOnComplete(() -> log.info("[mw:logging] <- onAgent  done in {} ms",
                        (System.nanoTime() - t0) / 1_000_000))
                .doOnError(err -> log.warn("[mw:logging] <- onAgent  err={}", err.toString()));
    }

    @Override
    public Flux<AgentEvent> onReasoning(Agent agent,
                                         RuntimeContext ctx,
                                         ReasoningInput input,
                                         Function<ReasoningInput, Flux<AgentEvent>> next) {
        log.info("[mw:logging] -> onReasoning  messages={} tools={} options={}",
                input.messages().size(), input.tools().size(),
                input.options() == null ? "null" : "present");
        return next.apply(input);
    }

    @Override
    public Flux<AgentEvent> onModelCall(Agent agent,
                                         RuntimeContext ctx,
                                         ModelCallInput input,
                                         Function<ModelCallInput, Flux<AgentEvent>> next) {
        log.info("[mw:logging] -> onModelCall model={} messages={}",
                input.model().getClass().getSimpleName(), input.messages().size());
        return next.apply(input)
                .doOnComplete(() -> log.info("[mw:logging] <- onModelCall done"));
    }

    @Override
    public Mono<String> onSystemPrompt(Agent agent, RuntimeContext ctx, String currentPrompt) {
        log.info("[mw:logging] -> onSystemPrompt len={}", currentPrompt.length());
        return Mono.just(currentPrompt);
    }
}
