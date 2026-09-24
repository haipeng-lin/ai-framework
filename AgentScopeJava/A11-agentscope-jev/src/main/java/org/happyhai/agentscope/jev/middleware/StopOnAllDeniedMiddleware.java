package org.happyhai.agentscope.jev.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AllToolsDeniedEvent;
import io.agentscope.core.event.RequestStopEvent;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.MiddlewareBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;
import reactor.core.publisher.Flux;

/**
 * 演示 onActing hook + 中间件驱动 stop：
 * 当用户在 HITL 中拒绝了本轮的所有工具调用时，agent 默认会继续下一轮推理；
 * 装配本中间件后，会观察 AllToolsDeniedEvent 并注入一个 RequestStopEvent，
 * 让 agent 立即停止，返回 GenerateReason.ALL_TOOLS_DENIED。
 */
public class StopOnAllDeniedMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(StopOnAllDeniedMiddleware.class);

    @Override
    public Flux<AgentEvent> onActing(Agent agent,
                                      RuntimeContext ctx,
                                      ActingInput input,
                                      Function<ActingInput, Flux<AgentEvent>> next) {
        return next.apply(input)
                .flatMap(event -> {
                    if (event instanceof AllToolsDeniedEvent denied) {
                        log.info("[mw:stop-on-deny] saw AllToolsDeniedEvent ({} calls) -> emitting RequestStopEvent",
                                denied.getDeniedToolCalls().size());
                        return Flux.just(
                                event,
                                new RequestStopEvent(
                                        "All tools denied by user",
                                        GenerateReason.ALL_TOOLS_DENIED));
                    }
                    return Flux.just(event);
                });
    }
}
