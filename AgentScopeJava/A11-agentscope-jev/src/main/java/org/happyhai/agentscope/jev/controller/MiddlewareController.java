package org.happyhai.agentscope.jev.controller;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.message.Msg;
import io.agentscope.harness.agent.HarnessAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A06 中间件演示控制器：
 * <ul>
 *   <li>GET  /agent/chat/stream  SSE 流式对话</li>
 *   <li>POST /agent/chat/reply   同步对话，演示 call(...) 路径上的 middleware</li>
 *   <li>GET  /agent/middlewares  列出当前装配的 middleware 元信息（顺序、order、覆盖的 hook）</li>
 * </ul>
 *
 * <p>每个中间件都会在 Spring Boot 控制台打出运行日志，便于直接观察效果。
 */
@RestController
@RequestMapping("/agent")
public class MiddlewareController {

    private static final Logger log = LoggerFactory.getLogger(MiddlewareController.class);

    private final HarnessAgent agent;
    private final List<MiddlewareBase> installedMiddlewares;

    public MiddlewareController(HarnessAgent agent, List<MiddlewareBase> installedMiddlewares) {
        this.agent = agent;
        this.installedMiddlewares = installedMiddlewares;
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
                .doOnError(err -> closeWithError(emitter, err))
                .doOnComplete(() -> sendDone(emitter))
                .subscribe();

        return emitter;
    }

    @PostMapping("/chat/reply")
    public Map<String, Object> reply(@RequestParam("sessionId") String sessionId,
                                     @RequestParam("userId") String userId,
                                     @RequestParam("prompt") String prompt) {
        RuntimeContext rc = RuntimeContext.builder()
                .sessionId(sessionId)
                .userId(userId)
                .build();
        Msg result = agent.call(prompt, rc).block();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("output", result == null ? "" : result.getTextContent());
        body.put("generateReason",
                result == null || result.getGenerateReason() == null
                        ? null : result.getGenerateReason().name());
        return body;
    }

    @GetMapping("/middlewares")
    public List<Map<String, Object>> listMiddlewares() {
        return installedMiddlewares.stream().map(MiddlewareController::describe).toList();
    }

    private static Map<String, Object> describe(MiddlewareBase mw) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("class", mw.getClass().getSimpleName());
        view.put("order", mw.order());
        view.put("hooks", List.of(
                "onAgent:" + (isOverridden(mw, "onAgent") ? "Y" : "n"),
                "onReasoning:" + (isOverridden(mw, "onReasoning") ? "Y" : "n"),
                "onActing:" + (isOverridden(mw, "onActing") ? "Y" : "n"),
                "onModelCall:" + (isOverridden(mw, "onModelCall") ? "Y" : "n"),
                "onSystemPrompt:" + (isOverridden(mw, "onSystemPrompt") ? "Y" : "n")));
        return view;
    }

    /** 简单判断 hook 是否被覆写——若仍等于接口里的默认实现就认为没覆写。 */
    private static boolean isOverridden(MiddlewareBase mw, String hookName) {
        for (java.lang.reflect.Method m : mw.getClass().getMethods()) {
            if (m.getName().equals(hookName)
                    && m.getDeclaringClass() != MiddlewareBase.class) {
                return true;
            }
        }
        return false;
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
            } else if (type == AgentEventType.ALL_TOOLS_DENIED) {
                emitter.send(SseEmitter.event().name("all_tools_denied").data(event.toString()));
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
            // best-effort trailing event
        } finally {
            emitter.complete();
        }
    }

    private void closeWithError(SseEmitter emitter, Throwable err) {
        log.error("SSE stream error", err);
        try {
            emitter.send(SseEmitter.event().name("error").data(String.valueOf(err.getMessage())));
        } catch (IOException ignored) {
        }
        emitter.completeWithError(err);
    }

}
