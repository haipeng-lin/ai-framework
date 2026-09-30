package org.happyhai.agentscope.mcp.client.controller;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.harness.agent.HarnessAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 与 A04 的 AgentToolStreamController 等价。区别在于：
 * <p>
 * 1. 工具列表来自远端 B01 MCP Server（Toolkit.registerMcpClient）；
 * <p>
 * 2. HarnessAgent 的 streamEvents 在「调了工具但模型没有产出文本」的回合里
 * 会直接结束（消息结束在 tool-call turn）。此时客户端只会看到 done 事件，
 * 没有任何 text 增量。这里检测这种情况，自动再发一句
 * "请用自然语言总结刚才工具调用的结果并回复用户。"
 * 让 agent 在同一个 SSE 流里继续产出最终回复。
 */
@RestController
@RequestMapping("/agent")
public class AgentMcpStreamController {

    private static final Logger log = LoggerFactory.getLogger(AgentMcpStreamController.class);

    private static final String SUMMARY_PROMPT =
            "请用自然语言总结你刚才工具调用的结果并直接回复用户，不要再调用任何工具。";

    private final HarnessAgent agent;

    public AgentMcpStreamController(HarnessAgent agent) {
        this.agent = agent;
    }

    @GetMapping(path = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestParam("userId") String userId,
                                 @RequestParam("sessionId") String sessionId,
                                 @RequestParam("prompt") String prompt) {
        SseEmitter emitter = new SseEmitter(0L);
        RuntimeContext rc = RuntimeContext.builder()
                .sessionId(sessionId)
                .userId(userId)
                .build();

        AtomicBoolean sawText = new AtomicBoolean(false);

        agent.streamEvents(prompt, rc)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(event -> {
                    if (dispatch(emitter, event)
                            && event.getType() == AgentEventType.TEXT_BLOCK_DELTA) {
                        sawText.set(true);
                    }
                })
                .doOnError(err -> {
                    log.error("Agent stream error for sessionId={}, userId={}", sessionId, userId, err);
                    emitter.completeWithError(err);
                })
                .doOnComplete(() -> {
                    if (!sawText.get()) {
                        log.debug("No text emitted in first turn for sessionId={}, prompting for summary.", sessionId);
                        sendFollowUpSummary(emitter, rc);
                        return;
                    }
                    finishStream(emitter);
                })
                .subscribe();

        return emitter;
    }

    /**
     * ReAct loop 结束在 tool-call 回合时，追加一个总结回合。
     */
    private void sendFollowUpSummary(SseEmitter emitter, RuntimeContext rc) {
        Msg followUp = Msg.builder()
                .role(MsgRole.USER)
                .name("user")
                .content(List.of(TextBlock.builder().text(SUMMARY_PROMPT).build()))
                .build();

        agent.streamEvents(followUp, rc)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(event -> dispatch(emitter, event))
                .doOnError(err -> {
                    log.error("Follow-up summary stream error", err);
                    emitter.completeWithError(err);
                })
                .doOnComplete(() -> finishStream(emitter))
                .subscribe();
    }

    private void finishStream(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
        } catch (IOException ignored) {
            // best-effort trailing event
        } finally {
            emitter.complete();
        }
    }

    private boolean dispatch(SseEmitter emitter, AgentEvent event) {
        try {
            AgentEventType type = event.getType();
            if (type == AgentEventType.TEXT_BLOCK_DELTA || type == AgentEventType.THINKING_BLOCK_DELTA) {
                String delta = ((TextBlockDeltaEvent) event).getDelta();
                if (delta != null && !delta.isEmpty()) {
                    String name = type == AgentEventType.THINKING_BLOCK_DELTA ? "thinking" : "text";
                    emitter.send(SseEmitter.event().name(name).data(delta));
                    return true;
                }
            }
        } catch (IOException ex) {
            emitter.completeWithError(ex);
            return false;
        }
        return true;
    }
}
