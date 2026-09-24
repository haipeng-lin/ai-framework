package org.happyhai.agentscope.jev.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.function.Function;

/**
 * 演示 onModelCall hook：在模型 API 调用前后计时。
 *
 * <p>用 doFinally 信号收尾，无论 next 是正常完成还是抛错都会打印耗时。
 */
public class TimingMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(TimingMiddleware.class);

    @Override
    public Flux<AgentEvent> onModelCall(Agent agent,
                                        RuntimeContext ctx,
                                        ModelCallInput input,
                                        Function<ModelCallInput, Flux<AgentEvent>> next) {
        long start = System.nanoTime();
        return next.apply(input)
                .doFinally(sig -> {
                    long ms = (System.nanoTime() - start) / 1_000_000;
                    log.info("[mw:timing] model_call finished: agent={} sig={} elapsed={} ms",
                            agent.getName(), sig, ms);
                });
    }
}
