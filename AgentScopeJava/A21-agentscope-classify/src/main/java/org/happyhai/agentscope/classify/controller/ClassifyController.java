package org.happyhai.agentscope.classify.controller;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import org.happyhai.agentscope.classify.intent.IntentResult;
import org.happyhai.agentscope.classify.intent.IntentRouter;
import org.happyhai.agentscope.classify.orchestrator.AquariumLightOrchestrator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 同步 API：纯分类、纯编排（一次性同步返回），便于 curl/Postman 联调。
 *
 * <p>路径前缀 {@code /intelligence}，与流式入口 {@code /orchestrator} 解耦。
 */
@RestController
@RequestMapping("/intelligence")
public class ClassifyController {

    private final AquariumLightOrchestrator orchestrator;

    public ClassifyController(AquariumLightOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /** 只跑意图分类，不委派。 */
    @GetMapping("/classify")
    public Map<String, Object> classify(@RequestParam("prompt") String prompt) {
        IntentResult result = orchestrator.classifyOnly(prompt);
        IntentRouter.RouteDecision decision = orchestrator.routeFor(prompt);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("classification", IntentRouter.describe(result, decision));
        return body;
    }

    /** 同步跑全流程：分类 + 路由 + 委派 + 返回 Agent 最终回复。 */
    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestParam("userId") String userId,
                                    @RequestParam("sessionId") String sessionId,
                                    @RequestParam("prompt") String prompt) {
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId(sessionId)
                .userId(userId)
                .build();
        AquariumLightOrchestrator.OrchestrationOutcome outcome = orchestrator.handle(prompt, ctx);
        Msg reply = outcome.reply();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("classification", IntentRouter.describe(outcome.intent(), outcome.route()));
        body.put("trace", outcome.trace());
        body.put("output", reply == null ? "" : reply.getTextContent());
        return body;
    }
}
