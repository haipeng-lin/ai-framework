 package org.happyhai.agentscope.harness.skill.controller;

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
  * 提供 SSE 流式对话：用户在浏览器输入身高体重，agent 自主加载 bmi-calculator skill 并计算。
  */
 @RestController
 @RequestMapping("/agent")
 public class AgentSkillStreamController {

     private static final Logger log = LoggerFactory.getLogger(AgentSkillStreamController.class);

     private final HarnessAgent agent;

     public AgentSkillStreamController(HarnessAgent agent) {
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
                     log.error("SSE 流错误 sessionId={} userId={}", sessionId, userId, err);
                     try {
                         emitter.send(SseEmitter.event().name("error")
                                 .data(String.valueOf(err.getMessage())));
                     } catch (IOException ignored) {
                     }
                     emitter.completeWithError(err);
                 })
                 .doOnComplete(() -> {
                     try {
                         emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                     } catch (IOException ignored) {
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
                 // 把 skill 工具（load_skill_through_path / read_skill）的返回结果打到前端，便于观察 agent
                 // 究竟读到了 SKILL.md / references/ 的哪一段。
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
