package org.happyhai.agentscope.classify.controller;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import org.happyhai.agentscope.classify.intent.IntentResult;
import org.happyhai.agentscope.classify.intent.IntentRouter;
import org.happyhai.agentscope.classify.orchestrator.AquariumLightOrchestrator;
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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 编排层 SSE 入口。
 *
 * <p>响应事件：
 * <ul>
 *   <li>{@code intent}   — 第一帧返回分类结果（确定性路由已就绪）</li>
 *   <li>{@code text}     — 委派子 Agent 的文本增量</li>
 *   <li>{@code thinking} — 委派子 Agent 的思维链增量</li>
 *   <li>{@code tool_*}/ {@code agent_end}/ {@code error}/ {@code done} — 透传 Agent 事件</li>
 * </ul>
 */
@RestController
@RequestMapping("/orchestrator")
public class OrchestratorStreamController {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorStreamController.class);

    private final AquariumLightOrchestrator orchestrator;

    public OrchestratorStreamController(AquariumLightOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @GetMapping(path = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam("userId") String userId,
                             @RequestParam("sessionId") String sessionId,
                             @RequestParam("prompt") String prompt) {
        SseEmitter emitter = new SseEmitter(0L);
        RuntimeContext rc = RuntimeContext.builder()
                .sessionId(sessionId)
                .userId(userId)
                .build();

        // 同步阶段先做分类，立刻把意图帧推给前端（10ms 内可见路由结果）
        try {
            IntentResult intent = orchestrator.classifyOnly(prompt);
            IntentRouter.RouteDecision decision = orchestrator.routeFor(prompt);
            Map<String, Object> intentFrame = IntentRouter.describe(intent, decision);
            emitter.send(SseEmitter.event().name("intent").data(intentFrame));
        } catch (IOException io) {
            emitter.completeWithError(io);
            return emitter;
        }

        orchestrator.handleStream(prompt, rc)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(event -> dispatch(emitter, event))
                .doOnError(err -> {
                    log.error("Orchestrator stream error for sessionId={}, userId={}", sessionId, userId, err);
                    try {
                        emitter.send(SseEmitter.event().name("error").data(String.valueOf(err.getMessage())));
                    } catch (IOException ignored) {
                    }
                    emitter.completeWithError(err);
                })
                .doOnComplete(() -> sendDone(emitter))
                .subscribe();
        return emitter;
    }

    private void dispatch(SseEmitter emitter, AgentEvent event) {
        try {
            AgentEventType type = event.getType();
            if (type == AgentEventType.TEXT_BLOCK_DELTA) {
                String delta = ((TextBlockDeltaEvent) event).getDelta();
                if (delta != null && !delta.isEmpty()) {
                    emitter.send(SseEmitter.event().name("text").data(delta));
                }
            } else if (type == AgentEventType.THINKING_BLOCK_DELTA) {
                String delta = ((TextBlockDeltaEvent) event).getDelta();
                if (delta != null && !delta.isEmpty()) {
                    emitter.send(SseEmitter.event().name("thinking").data(delta));
                }
            } else if (type == AgentEventType.TOOL_CALL_START) {
                emitter.send(SseEmitter.event().name("tool_call_start").data(event.toString()));
            } else if (type == AgentEventType.TOOL_RESULT_END) {
                emitter.send(SseEmitter.event().name("tool_call_end").data(event.toString()));
            } else if (type == AgentEventType.REQUEST_STOP) {
                emitter.send(SseEmitter.event().name("request_stop").data(event.toString()));
            } else if (type == AgentEventType.AGENT_END) {
                emitter.send(SseEmitter.event().name("agent_end").data(event.toString()));
            }
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
    }

    private void sendDone(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
        } catch (IOException ignored) {
        } finally {
            emitter.complete();
        }
    }

    /** 健康检查。 */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("agents", java.util.List.of(
                "chatAgent", "knowledgeAgent", "explicitControlAgent",
                "fuzzyControlAgent", "statusQueryAgent", "orchestratorAgent"));
        return body;
    }
}
