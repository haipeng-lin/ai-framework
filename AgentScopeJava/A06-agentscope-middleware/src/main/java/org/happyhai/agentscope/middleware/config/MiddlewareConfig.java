package org.happyhai.agentscope.middleware.config;

import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.tracing.OtelTracingMiddleware;
import org.happyhai.agentscope.middleware.middleware.DynamicContextMiddleware;
import org.happyhai.agentscope.middleware.middleware.FinalAnswerFilterMiddleware;
import org.happyhai.agentscope.middleware.middleware.LoggingMiddleware;
import org.happyhai.agentscope.middleware.middleware.RateLimitMiddleware;
import org.happyhai.agentscope.middleware.middleware.RequestContextMiddleware;
import org.happyhai.agentscope.middleware.middleware.StopOnAllDeniedMiddleware;
import org.happyhai.agentscope.middleware.middleware.TimingMiddleware;
import org.happyhai.agentscope.middleware.middleware.ToolInvocationLogMiddleware;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 把所有演示用的 middleware 注册为 Spring bean，便于装配到 HarnessAgent。
 *
 * <p>默认装配顺序（最外层 → 最内层）：
 * <pre>
 *   LoggingMiddleware(order=10)
 *   ToolInvocationLogMiddleware
 *   StopOnAllDeniedMiddleware
 *   RateLimitMiddleware
 *   TimingMiddleware
 *   RequestContextMiddleware
 *   DynamicContextMiddleware
 *   FinalAnswerFilterMiddleware（可选）
 *   OtelTracingMiddleware（若启用）
 * </pre>
 * 注意 Transformer 类型的 DynamicContextMiddleware 是从左到右串行接力的，
 * 所以它要放在需要它变换的 prompt 形成之后；本例里放在最后追加上下文。
 */
@Configuration
public class MiddlewareConfig {

    @Value("${middleware.rate-limit.min-interval-ms:0}")
    private long rateLimitMinIntervalMs;

    @Value("${middleware.final-answer-filter.enabled:false}")
    private boolean finalAnswerFilterEnabled;

    @Bean
    public LoggingMiddleware loggingMiddleware() {
        return new LoggingMiddleware();
    }

    @Bean
    public TimingMiddleware timingMiddleware() {
        return new TimingMiddleware();
    }

    @Bean
    public RateLimitMiddleware rateLimitMiddleware() {
        RateLimitMiddleware mw = new RateLimitMiddleware(Duration.ofMillis(rateLimitMinIntervalMs));
        mw.logConfig();
        return mw;
    }

    @Bean
    public DynamicContextMiddleware dynamicContextMiddleware() {
        return new DynamicContextMiddleware();
    }

    @Bean
    public RequestContextMiddleware requestContextMiddleware() {
        return new RequestContextMiddleware();
    }

    @Bean
    public StopOnAllDeniedMiddleware stopOnAllDeniedMiddleware() {
        return new StopOnAllDeniedMiddleware();
    }

    @Bean
    public ToolInvocationLogMiddleware toolInvocationLogMiddleware() {
        // 默认开启；如有需要可改成 @Value 读取配置开关
        return new ToolInvocationLogMiddleware(true);
    }

    @Bean
    public FinalAnswerFilterMiddleware finalAnswerFilterMiddleware() {
        return new FinalAnswerFilterMiddleware();
    }

    @Bean
    public OtelTracingMiddleware otelTracingMiddleware() {
        // 未配置 OTel SDK 时，所有 hook 短路到 next.apply(input)，几乎零开销。
        return new OtelTracingMiddleware();
    }

    /**
     * 装配到 agent 的 middleware 列表。order 大的在外层；Transformer（onSystemPrompt）
     * 按列表顺序从左到右接力。
     */
    @Bean
    public List<MiddlewareBase> installedMiddlewares(LoggingMiddleware logging,
                                                      StopOnAllDeniedMiddleware stopOnDeny,
                                                      RateLimitMiddleware rateLimit,
                                                      TimingMiddleware timing,
                                                      RequestContextMiddleware reqCtx,
                                                      DynamicContextMiddleware dynamicCtx,
                                                      FinalAnswerFilterMiddleware finalFilter,
                                                      OtelTracingMiddleware otel,
                                                      ToolInvocationLogMiddleware toolLog) {
        List<MiddlewareBase> list = new ArrayList<>();
        list.add(logging);       // order=10 最外层
        list.add(toolLog);       // 工具入参 / 结果审计，紧贴 logging 之下
        list.add(stopOnDeny);    // 观察 acting 事件
        list.add(rateLimit);     // 模型调用节流
        list.add(timing);        // 模型调用计时
        list.add(reqCtx);        // 读 / 写 RuntimeContext
        list.add(dynamicCtx);    // Transformer：把运行时上下文追加到 system prompt
        if (finalAnswerFilterEnabled) {
            list.add(finalFilter); // 只保留最终轮文本
        }
        list.add(otel);          // 始终装上，未配置 OTel SDK 时即 no-op
        return list;
    }
}
