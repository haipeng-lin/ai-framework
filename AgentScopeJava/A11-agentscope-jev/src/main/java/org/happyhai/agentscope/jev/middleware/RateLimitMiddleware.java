package org.happyhai.agentscope.jev.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 演示 onModelCall hook：在两次模型调用之间强制留出最小间隔。
 *
 * <p>通过原子变量记录上次调用的订阅时刻；下次调用时若间隔不足则先 sleep。
 */
public class RateLimitMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(RateLimitMiddleware.class);

    private final long minIntervalMs;
    private final AtomicLong lastSubscribeAt = new AtomicLong(0);

    public RateLimitMiddleware(Duration minInterval) {
        this.minIntervalMs = Math.max(0, minInterval.toMillis());
    }

    @Override
    public Flux<AgentEvent> onModelCall(Agent agent,
                                        RuntimeContext ctx,
                                        ModelCallInput input,
                                        Function<ModelCallInput, Flux<AgentEvent>> next) {
        if (minIntervalMs <= 0) {
            return next.apply(input);
        }
        long now = System.currentTimeMillis();
        long wait = minIntervalMs - (now - lastSubscribeAt.get());
        Mono<Void> delay = wait > 0
                ? Mono.delay(Duration.ofMillis(wait)).then()
                : Mono.empty();
        return delay.thenMany(next.apply(input))
                .doOnSubscribe(s -> lastSubscribeAt.set(System.currentTimeMillis()));
    }

    public long getMinIntervalMs() {
        return minIntervalMs;
    }

    // build() 阶段打一行参数日志，方便启动时确认配置
    public void logConfig() {
        log.info("[mw:rate-limit] configured minIntervalMs={}", minIntervalMs);
    }
}
