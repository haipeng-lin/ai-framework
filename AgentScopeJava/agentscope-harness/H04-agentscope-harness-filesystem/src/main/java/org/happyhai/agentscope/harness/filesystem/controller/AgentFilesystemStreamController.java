package org.happyhai.agentscope.harness.filesystem.controller;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.message.ToolResultState;
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

/**
 * SSE 流式对话接口：前端拿到 agent 的文本流、思考流和工具调用结果。
 * <p>
 * 调用示例（浏览器或 curl）：
 * <pre>{@code
 * curl "http://localhost:20004/agent/stream/chat?userId=alice&sessionId=s01&prompt=记住我最喜欢的颜色是蓝色"
 * }</pre>
 * <p>
 * 验证手段：调用完上面的接口后，再调用
 * {@code /agent/filesystem/inspect/list?userId=alice} 或
 * {@code /agent/filesystem/inspect/rawRedis?pattern=filesystem-shared-store-agent*}
 * 查看 Redis 里实际写了什么。
 */
@RestController
@RequestMapping("/agent/stream")
public class AgentFilesystemStreamController {

    private static final Logger log = LoggerFactory.getLogger(AgentFilesystemStreamController.class);

    private final HarnessAgent agent;

    public AgentFilesystemStreamController(HarnessAgent agent) {
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
                    log.error("SSE 流错误 sessionId={} userId={}", sessionId, userId, err);
                    try {
                        emitter.send(SseEmitter.event().name("error")
                                .data(String.valueOf(err.getMessage())));
                    } catch (IOException ignored) {
                        // 客户端已经断开，无需重试
                    }
                    emitter.completeWithError(err);
                })
                .doOnComplete(() -> {
                    try {
                        emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                    } catch (IOException ignored) {
                        // 客户端已经断开，无需重试
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
                    emitter.send(SseEmitter.event().name("text").data(delta));
                }
            } else if (type == AgentEventType.THINKING_BLOCK_DELTA) {
                String delta = ((TextBlockDeltaEvent) event).getDelta();
                if (delta != null && !delta.isEmpty()) {
                    emitter.send(SseEmitter.event().name("thinking").data(delta));
                }
            } else if (type == AgentEventType.TOOL_RESULT_END) {
                // 把工具返回打到前端，方便观察 agent 真的去写了 memory 文件
                ToolResultEndEvent e = (ToolResultEndEvent) event;
                ToolResultState state = e.getState();
                emitter.send(SseEmitter.event().name("tool_result")
                        .data("name=" + e.getToolCallName()
                                + " state=" + (state == null ? "UNKNOWN" : state.name())
                                + " callId=" + e.getToolCallId()));
            }
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
    }
}
