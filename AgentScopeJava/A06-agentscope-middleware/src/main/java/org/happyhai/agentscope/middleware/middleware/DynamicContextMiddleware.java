package org.happyhai.agentscope.middleware.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.middleware.MiddlewareBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;

/**
 * 演示 onSystemPrompt hook（Transformer 类型）：在前一个 middleware 输出后做一次变换。
 *
 * <p>把当前时间与时区附加到 system prompt 末尾。Supplier 在每次调用时取值，方便做动态上下文注入。
 */
public class DynamicContextMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(DynamicContextMiddleware.class);

    private final Supplier<String> contextFn;

    public DynamicContextMiddleware() {
        this(DynamicContextMiddleware::defaultContext);
    }

    public DynamicContextMiddleware(Supplier<String> contextFn) {
        this.contextFn = contextFn;
    }

    @Override
    public Mono<String> onSystemPrompt(Agent agent, RuntimeContext ctx, String currentPrompt) {
        String extra = contextFn.get();
        log.info("[mw:dynamic-ctx] 向 system prompt 注入运行时上下文，共 {} 个字符", extra.length());
        return Mono.just(currentPrompt + "\n\n## Runtime Context\n" + extra);
    }

    private static String defaultContext() {
        Instant now = Instant.now();
        return "now=" + now + " ("
                + DateTimeFormatter.ISO_INSTANT.format(now.atZone(ZoneId.systemDefault()))
                + ", zone=" + ZoneId.systemDefault().getId() + ")";
    }
}
