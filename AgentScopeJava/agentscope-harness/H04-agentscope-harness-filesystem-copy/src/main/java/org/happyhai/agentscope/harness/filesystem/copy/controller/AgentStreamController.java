package org.happyhai.agentscope.harness.filesystem.copy.controller;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.harness.agent.HarnessAgent;
import org.happyhai.agentscope.harness.filesystem.copy.config.ReplicaIdentity;
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

/**
 * 多副本测试用的 SSE 流接口。跟 H04 的差异：
 * <ul>
 *   <li>每个 SSE event 的 name 前缀都会带上 {@code replica-id}（例如 {@code [replica-B]text}），
 *       多副本对比时一眼看出是哪个进程发出的。</li>
 *   <li>发送第一条 event 之前先打一个 {@code started} 事件，把副本身份（id、agent-name、
 *       workspace 路径）直接 push 给前端。</li>
 * </ul>
 */
@RestController
@RequestMapping("/agent/stream")
public class AgentStreamController {

    private static final Logger log = LoggerFactory.getLogger(AgentStreamController.class);

    private final HarnessAgent agent;
    private final ReplicaIdentity identity;

    public AgentStreamController(HarnessAgent agent, ReplicaIdentity identity) {
        this.agent = agent;
        this.identity = identity;
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

        log.info("[{}] 接收到 chat：userId={}, sessionId={}", identity.replicaId(), userId, sessionId);

        // 先发一个 started 事件，把副本身份告诉前端
        try {
            emitter.send(SseEmitter.event().name(identity.replicaId() + ":started")
                    .data("replicaId=" + identity.replicaId()
                            + " agentName=" + identity.agentName()
                            + " workspace=" + identity.workspaceDir()));
        } catch (IOException ignored) {
            // 客户端已断开就走正常错误分支
        }

        agent.streamEvents(prompt, rc)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(event -> dispatch(emitter, event))
                .doOnError(err -> {
                    log.error("[{}] SSE 流错误 sessionId={} userId={}", identity.replicaId(), sessionId, userId, err);
                    try {
                        emitter.send(SseEmitter.event().name(identity.replicaId() + ":error")
                                .data(String.valueOf(err.getMessage())));
                    } catch (IOException ignored) {
                        // 客户端已断开
                    }
                    emitter.completeWithError(err);
                })
                .doOnComplete(() -> {
                    try {
                        emitter.send(SseEmitter.event().name(identity.replicaId() + ":done").data("[DONE]"));
                    } catch (IOException ignored) {
                        // 客户端已断开
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
            if (type == AgentEventType.TEXT_BLOCK_DELTA) {
                String delta = ((TextBlockDeltaEvent) event).getDelta();
                if (delta != null && !delta.isEmpty()) {
                    emitter.send(SseEmitter.event().name(identity.replicaId() + ":text").data(delta));
                }
            } else if (type == AgentEventType.THINKING_BLOCK_DELTA) {
                String delta = ((TextBlockDeltaEvent) event).getDelta();
                if (delta != null && !delta.isEmpty()) {
                    emitter.send(SseEmitter.event().name(identity.replicaId() + ":thinking").data(delta));
                }
            } else if (type == AgentEventType.TOOL_RESULT_END) {
                ToolResultEndEvent e = (ToolResultEndEvent) event;
                ToolResultState state = e.getState();
                emitter.send(SseEmitter.event().name(identity.replicaId() + ":tool_result")
                        .data("name=" + e.getToolCallName()
                                + " state=" + (state == null ? "UNKNOWN" : state.name())
                                + " callId=" + e.getToolCallId()));
            }
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
    }
}
