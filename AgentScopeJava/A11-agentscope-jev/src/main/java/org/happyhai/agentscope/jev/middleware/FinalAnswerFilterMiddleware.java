package org.happyhai.agentscope.jev.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ReasoningInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import reactor.core.publisher.Flux;

/**
 * 自定义实现的 FinalAnswerFilterMiddleware：只输出 ReAct 最终推理轮的文本。
 *
 * <p>2.0.1 的 agentscope-core 中尚未内置该中间件，本类手动复现官方文档描述的语义：
 * <ul>
 *   <li>在 onReasoning 内累积本轮所有 TEXT_BLOCK_DELTA 文本来缓冲。</li>
 *   <li>抑制这些 DELTA 不向 next 外发出。</li>
 *   <li>在 onReasoning 流末尾判断本轮是否产生了工具调用：没有则把累计文本作为
 *       一个 TEXT_BLOCK_DELTA 整块发出，否则丢弃。</li>
 * </ul>
 *
 * <p>其他事件（TEXT_BLOCK_START / TEXT_BLOCK_END / TOOL_CALL_* / MODEL_CALL_* 等）
 * 按原样透传。
 */
public class FinalAnswerFilterMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(FinalAnswerFilterMiddleware.class);

    @Override
    public Flux<AgentEvent> onReasoning(Agent agent,
                                         RuntimeContext ctx,
                                         ReasoningInput input,
                                         Function<ReasoningInput, Flux<AgentEvent>> next) {
        StringBuilder buffer = new StringBuilder();
        AtomicBoolean toolCalled = new AtomicBoolean(false);
        AtomicReference<String> replyId = new AtomicReference<>();
        AtomicReference<String> blockId = new AtomicReference<>();

        return next.apply(input)
                .doOnNext(event -> {
                    AgentEventType type = event.getType();
                    if (type == AgentEventType.TEXT_BLOCK_DELTA) {
                        TextBlockDeltaEvent e = (TextBlockDeltaEvent) event;
                        if (e.getDelta() != null) {
                            buffer.append(e.getDelta());
                        }
                        if (replyId.get() == null) {
                            replyId.set(e.getReplyId());
                        }
                        if (blockId.get() == null) {
                            blockId.set(e.getBlockId());
                        }
                    } else if (type == AgentEventType.TOOL_CALL_START) {
                        toolCalled.set(true);
                    } else if (type == AgentEventType.MODEL_CALL_END) {
                        ModelCallEndEvent e = (ModelCallEndEvent) event;
                        if (replyId.get() == null) {
                            replyId.set(e.getReplyId());
                        }
                    }
                })
                // 把 DELTA 留给下一阶段合并后再发，否则文本会零碎流出
                .filter(event -> event.getType() != AgentEventType.TEXT_BLOCK_DELTA)
                .concatWith(Flux.defer(() -> {
                    if (toolCalled.get() || buffer.length() == 0) {
                        if (toolCalled.get()) {
                            log.debug("[mw:final-answer-filter] round produced tool calls -> drop buffered text ({} chars)",
                                    buffer.length());
                        }
                        return Flux.empty();
                    }
                    String rid = replyId.get();
                    String bid = blockId.get() == null ? "final-answer" : blockId.get();
                    TextBlockDeltaEvent finalDelta = new TextBlockDeltaEvent(rid, bid, buffer.toString());
                    log.info("[mw:final-answer-filter] emitting final answer ({} chars)", buffer.length());
                    return Flux.just(finalDelta);
                }));
    }
}
