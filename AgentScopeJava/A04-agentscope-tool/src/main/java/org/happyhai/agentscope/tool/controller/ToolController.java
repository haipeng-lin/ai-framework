package org.happyhai.agentscope.tool.controller;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.Toolkit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/tools")
public class ToolController {

    private final Toolkit toolkit;

    public ToolController(Toolkit toolkit) {
        this.toolkit = toolkit;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return toolkit.getToolSchemas().stream()
                .map(ToolController::toView)
                .toList();
    }

    @PostMapping("/{name}/invoke")
    public Map<String, Object> invoke(@PathVariable("name") String name,
                                      @RequestBody Map<String, Object> input) {
        if (toolkit.getTool(name) == null) {
            return Map.of(
                    "ok", false,
                    "error", "tool not found: " + name,
                    "available", toolkit.getToolNames()
            );
        }

        String callId = UUID.randomUUID().toString();
        ToolUseBlock useBlock = new ToolUseBlock(callId, name, input);
        ToolCallParam param = ToolCallParam.builder()
                .toolUseBlock(useBlock)
                .input(input)
                .build();

        ToolResultBlock result = toolkit.callTool(param).block();
        List<String> texts = result.getOutput().stream()
                .filter(TextBlock.class::isInstance)
                .map(b -> ((TextBlock) b).getText())
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("name", name);
        response.put("callId", callId);
        response.put("state", result.getState() == null ? null : result.getState().name());
        response.put("output", texts);
        return response;
    }

    private static Map<String, Object> toView(ToolSchema schema) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("name", schema.getName());
        view.put("description", schema.getDescription());
        view.put("parameters", schema.getParameters());
        return view;
    }

}
