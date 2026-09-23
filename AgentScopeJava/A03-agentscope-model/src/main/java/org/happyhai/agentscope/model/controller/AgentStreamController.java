package org.happyhai.agentscope.model.controller;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
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

@RestController
@RequestMapping("/agent/stream")
public class AgentStreamController {

    private static final Logger log = LoggerFactory.getLogger(AgentStreamController.class);

    private final HarnessAgent agent;

    public AgentStreamController(HarnessAgent agent) {
        this.agent = agent;
    }

    @GetMapping(path = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestParam("userId") String userId,
                                 @RequestParam("sessionId") String sessionId,
                                 @RequestParam("prompt") String prompt) {
        SseEmitter emitter = new SseEmitter(0L);
        RuntimeContext rc = RuntimeContext.builder()
                .sessionId(sessionId)
                .userId(userId)
                .build();

        agent.streamEvents(prompt, rc)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(event -> dispatch(emitter, event))
                .doOnError(err -> {
                    log.error("Agent stream error for sessionId={}, userId={}", sessionId, userId, err);
                    emitter.completeWithError(err);
                })
                .doOnComplete(() -> {
                    try {
                        emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                    } catch (IOException ignored) {
                        // best-effort trailing event; if the client is gone, complete quietly
                    } finally {
                        emitter.complete();
                    }
                })
                .subscribe();

        return emitter;
    }

    private void dispatch(SseEmitter emitter, AgentEvent event) {
        try {
            if (event.getType() == AgentEventType.TEXT_BLOCK_DELTA) {
                TextBlockDeltaEvent deltaEvent = (TextBlockDeltaEvent) event;
                String delta = deltaEvent.getDelta();
                if (delta != null && !delta.isEmpty()) {
                    emitter.send(SseEmitter.event().name("text").data(delta));
                }
            } else if (event.getType() == AgentEventType.THINKING_BLOCK_DELTA) {
                TextBlockDeltaEvent thinkingEvent = (TextBlockDeltaEvent) event;
                String delta = thinkingEvent.getDelta();
                if (delta != null && !delta.isEmpty()) {
                    emitter.send(SseEmitter.event().name("thinking").data(delta));
                }
            }
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
    }

}
