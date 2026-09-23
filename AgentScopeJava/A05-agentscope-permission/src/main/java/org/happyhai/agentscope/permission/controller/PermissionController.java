package org.happyhai.agentscope.permission.controller;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.UserConfirmResultEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.ToolUseBlock;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/chat")
public class PermissionController {

    private static final Logger log = LoggerFactory.getLogger(PermissionController.class);

    private final HarnessAgent agent;
    private final Map<String, List<ToolUseBlock>> pendingBySession = new ConcurrentHashMap<>();
    private final Map<String, StringBuilder> toolResultBuffers = new ConcurrentHashMap<>();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public PermissionController(HarnessAgent agent) {
        this.agent = agent;
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamStart(@RequestParam("userId") String userId,
                                  @RequestParam("sessionId") String sessionId,
                                  @RequestParam("prompt") String prompt) {
        SseEmitter emitter = new SseEmitter(0L);
        RuntimeContext rc = RuntimeContext.builder()
                .sessionId(sessionId)
                .userId(userId)
                .build();

        agent.streamEvents(prompt, rc)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(event -> dispatch(emitter, sessionId, event))
                .doOnError(err -> closeWithError(emitter, err))
                .doOnComplete(() -> sendDone(emitter))
                .subscribe();
        return emitter;
    }

    @GetMapping(path = "/resume-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamResume(@RequestParam("sessionId") String sessionId,
                                   @RequestParam("approvalId") String approvalId,
                                   @RequestParam("approved") boolean approved) {
        SseEmitter emitter = new SseEmitter(0L);
        Msg resumeMsg = buildResumeMessage(sessionId, approvalId, approved);
        if (resumeMsg == null) {
            closeWithError(emitter, new IllegalArgumentException("approvalId not found: " + approvalId));
            return emitter;
        }
        RuntimeContext rc = RuntimeContext.builder().sessionId(sessionId).build();
        agent.streamEvents(List.of(resumeMsg), rc)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(event -> dispatch(emitter, sessionId, event))
                .doOnError(err -> closeWithError(emitter, err))
                .doOnComplete(() -> {
                    pendingBySession.remove(sessionId);
                    sendDone(emitter);
                })
                .subscribe();
        return emitter;
    }

    /**
     * 同步版本，给非 SSE 的客户端用；行为跟旧的 /chat/confirm 一致。
     */
    @PostMapping("/confirm")
    public Map<String, Object> confirm(@RequestParam("sessionId") String sessionId,
                                       @RequestParam("approvalId") String approvalId,
                                       @RequestParam("approved") boolean approved) {
        List<ToolUseBlock> pending = pendingBySession.get(sessionId);
        if (pending == null) {
            return Map.of("status", "error", "message", "no pending approval for sessionId=" + sessionId);
        }
        ToolUseBlock target = null;
        for (ToolUseBlock t : pending) {
            if (approvalId.equals(t.getId())) {
                target = t;
                break;
            }
        }
        if (target == null) {
            return Map.of("status", "error", "message", "approvalId not found: " + approvalId);
        }
        ConfirmResult confirmResult = new ConfirmResult(approved, target);
        Map<String, Object> meta = new HashMap<>();
        meta.put(Msg.METADATA_CONFIRM_RESULTS, List.of(confirmResult));
        Msg resumeMsg = Msg.builder()
                .name("user")
                .role(MsgRole.USER)
                .textContent(approved ? "approved" : "denied")
                .metadata(meta)
                .build();
        RuntimeContext rc = RuntimeContext.builder().sessionId(sessionId).build();
        Msg finalResult = agent.call(List.of(resumeMsg), rc).block();
        pendingBySession.remove(sessionId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("output", finalResult == null ? "" : finalResult.getTextContent());
        body.put("generateReason",
                finalResult == null || finalResult.getGenerateReason() == null
                        ? null : finalResult.getGenerateReason().name());
        return body;
    }

    @PostMapping("/pendings")
    public Map<String, Object> pendings(@RequestParam("sessionId") String sessionId) {
        List<ToolUseBlock> pending = pendingBySession.get(sessionId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sessionId", sessionId);
        body.put("count", pending == null ? 0 : pending.size());
        body.put("items", pending == null ? List.of() : pending.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("approvalId", t.getId());
            m.put("toolName", t.getName());
            m.put("input", t.getInput());
            m.put("state", t.getState() == null ? null : t.getState().name());
            return m;
        }).toList());
        return body;
    }

    private Msg buildResumeMessage(String sessionId, String approvalId, boolean approved) {
        List<ToolUseBlock> pending = pendingBySession.get(sessionId);
        if (pending == null) {
            return null;
        }
        ToolUseBlock target = null;
        for (ToolUseBlock t : pending) {
            if (approvalId.equals(t.getId())) {
                target = t;
                break;
            }
        }
        if (target == null) {
            return null;
        }
        ConfirmResult cr = new ConfirmResult(approved, target);
        Map<String, Object> meta = new HashMap<>();
        meta.put(Msg.METADATA_CONFIRM_RESULTS, List.of(cr));
        return Msg.builder()
                .name("user")
                .role(MsgRole.USER)
                .textContent(approved ? "approved" : "denied")
                .metadata(meta)
                .build();
    }

    private void dispatch(SseEmitter emitter, String sessionId, AgentEvent event) {
        try {
            AgentEventType type = event.getType();
            if (type == AgentEventType.TEXT_BLOCK_DELTA) {
                String delta = ((TextBlockDeltaEvent) event).getDelta();
                emitText(emitter, "text", delta);
            } else if (type == AgentEventType.THINKING_BLOCK_DELTA) {
                String delta = ((TextBlockDeltaEvent) event).getDelta();
                emitText(emitter, "thinking", delta);
            } else if (type == AgentEventType.TOOL_RESULT_TEXT_DELTA) {
                ToolResultTextDeltaEvent d = (ToolResultTextDeltaEvent) event;
                String name = d.getToolCallName();
                if ("query_online_devices".equals(name)) {
                    toolResultBuffers
                            .computeIfAbsent(d.getToolCallId(), k -> new StringBuilder())
                            .append(d.getDelta());
                }
            } else if (type == AgentEventType.TOOL_RESULT_END) {
                ToolResultEndEvent end = (ToolResultEndEvent) event;
                String name = end.getToolCallName();
                if ("query_online_devices".equals(name)) {
                    StringBuilder buf = toolResultBuffers.remove(end.getToolCallId());
                    if (buf != null) {
                        emitter.send(SseEmitter.event().name("select_required").data(toSelectPayload(sessionId, end.getToolCallId(), buf.toString())));
                    }
                }
            } else if (type == AgentEventType.REQUIRE_USER_CONFIRM) {
                RequireUserConfirmEvent confirm = (RequireUserConfirmEvent) event;
                List<ToolUseBlock> calls = confirm.getToolCalls();
                pendingBySession.put(sessionId, calls);
                for (ToolUseBlock t : calls) {
                    emitter.send(SseEmitter.event().name("approval_required").data(toApprovalPayload(t, sessionId)));
                }
            } else if (type == AgentEventType.USER_CONFIRM_RESULT) {
                UserConfirmResultEvent resolved = (UserConfirmResultEvent) event;
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("replyId", resolved.getReplyId());
                payload.put("results", resolved.getConfirmResults().stream().map(cr -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("toolId", cr.getToolCall().getId());
                    m.put("toolName", cr.getToolCall().getName());
                    m.put("confirmed", cr.isConfirmed());
                    return m;
                }).toList());
                emitter.send(SseEmitter.event().name("approval_resolved").data(payload));
            }
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        } catch (RuntimeException ex) {
            log.warn("dispatch error: {}", ex.getMessage(), ex);
            emitter.completeWithError(ex);
        }
    }

    private static Map<String, Object> toApprovalPayload(ToolUseBlock t, String sessionId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("approvalId", t.getId());
        payload.put("toolName", t.getName());
        payload.put("input", t.getInput());
        payload.put("state", t.getState() == null ? null : t.getState().name());
        return payload;
    }

    private static Map<String, Object> toSelectPayload(String sessionId, String toolCallId, String json) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("toolCallId", toolCallId);
        payload.put("toolName", "query_online_devices");
        payload.put("question", "请告诉我要关哪些灯（可以多选，写 deviceId 或名字都行）");
        payload.put("multi", true);
        List<Map<String, String>> options;
        try {
            options = MAPPER.readValue(json, new TypeReference<List<Map<String, String>>>() {});
        } catch (Exception ex) {
            log.warn("select payload parse failed: {}", ex.getMessage());
            options = List.of();
        }
        payload.put("options", options);
        payload.put("raw", json);
        return payload;
    }

    private static void emitText(SseEmitter emitter, String eventName, String data) throws IOException {
        if (data == null || data.isEmpty()) {
            return;
        }
        emitter.send(SseEmitter.event().name(eventName).data(data));
    }

    private void closeWithError(SseEmitter emitter, Throwable err) {
        log.error("SSE stream error", err);
        try {
            emitter.send(SseEmitter.event().name("error").data(String.valueOf(err.getMessage())));
        } catch (IOException ignored) {
            // client gone; nothing to do
        }
        emitter.completeWithError(err);
    }

    private void sendDone(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
        } catch (IOException ignored) {
            // client gone; nothing to do
        } finally {
            emitter.complete();
        }
    }

}
    /**
     * 用户对 select_required 弹窗做出选择之后，把选择文本作为下一轮 user message 发回 agent；
     * 模型会接着推理（调用 toggle_light，再触发 permission ASK）。
     */
    @GetMapping(path = "/reply-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamReply(@RequestParam("sessionId") String sessionId,
                                  @RequestParam("text") String text) {
        SseEmitter emitter = new SseEmitter(0L);
        Msg userMsg = Msg.builder()
                .name("user")
                .role(MsgRole.USER)
                .textContent(text)
                .build();
        RuntimeContext rc = RuntimeContext.builder().sessionId(sessionId).build();
        agent.streamEvents(List.of(userMsg), rc)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(event -> dispatch(emitter, sessionId, event))
                .doOnError(err -> closeWithError(emitter, err))
                .doOnComplete(() -> sendDone(emitter))
                .subscribe();
        return emitter;
    }

    @PostMapping("/reply")
    public Map<String, Object> reply(@RequestParam("sessionId") String sessionId,
                                     @RequestParam("text") String text) {
        Msg userMsg = Msg.builder()
                .name("user")
                .role(MsgRole.USER)
                .textContent(text)
                .build();
        RuntimeContext rc = RuntimeContext.builder().sessionId(sessionId).build();
        Msg finalResult = agent.call(List.of(userMsg), rc).block();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("output", finalResult == null ? "" : finalResult.getTextContent());
        body.put("generateReason",
                finalResult == null || finalResult.getGenerateReason() == null
                        ? null : finalResult.getGenerateReason().name());
        return body;
    }
