package org.happyhai.agentscope.tool.controller;

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
@RequestMapping("/agent")
public class AgentToolStreamController {

    private static final Logger log = LoggerFactory.getLogger(AgentToolStreamController.class);

    private final HarnessAgent agent;

    public AgentToolStreamController(HarnessAgent agent) {
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
                        // best-effort trailing event
                    } finally {
                        emitter.complete();
                    }
                })
                .subscribe();

        return emitter;
    }

    private void dispatch(SseEmitter emitter, AgentEvent event) {
        try {
            AgentEventType type = event.getType();
            if (type == AgentEventType.TEXT_BLOCK_DELTA || type == AgentEventType.THINKING_BLOCK_DELTA) {
                String delta = ((TextBlockDeltaEvent) event).getDelta();
                if (delta != null && !delta.isEmpty()) {
                    String name = type == AgentEventType.THINKING_BLOCK_DELTA ? "thinking" : "text";
                    emitter.send(SseEmitter.event().name(name).data(delta));
                }
            }
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
    }

}
