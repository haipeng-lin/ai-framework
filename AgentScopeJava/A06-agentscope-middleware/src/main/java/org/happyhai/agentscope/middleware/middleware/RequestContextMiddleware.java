package org.happyhai.agentscope.middleware.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.function.Function;
import reactor.core.publisher.Flux;

/**
 * 演示 RuntimeContext 读写：
 * <ul>
 *   <li>读 RuntimeContext 中的 userId / sessionId / 自定义 request_id 并打印。</li>
 *   <li>写入一个本次 reply 唯一的 trace_id，下游 hook / tool 都能通过 ctx.get("trace_id") 读到。</li>
 * </ul>
 *
 * <p>注意：同一个 RuntimeContext 在整次 reply 内被各层共享，可以安全 put 写入。
 * 不要把请求级状态放在中间件实例字段上——一个 middleware 实例通常被多个 reply 复用。
 */
public class RequestContextMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(RequestContextMiddleware.class);

    public static final String TRACE_ID_KEY = "trace_id";

    @Override
    public Flux<AgentEvent> onAgent(Agent agent,
                                    RuntimeContext ctx,
                                    AgentInput input,
                                    Function<AgentInput, Flux<AgentEvent>> next) {
        log.info("[mw:req-ctx] onAgent 进入 agent={} user={} session={} reqId={}",
                agent.getName(), ctx.getUserId(), ctx.getSessionId(), ctx.get("request_id"));

        String traceId = UUID.randomUUID().toString();
        ctx.put(TRACE_ID_KEY, traceId);
        log.info("[mw:req-ctx] -> 已写入 trace_id={}", traceId);
        return next.apply(input);
    }
}
