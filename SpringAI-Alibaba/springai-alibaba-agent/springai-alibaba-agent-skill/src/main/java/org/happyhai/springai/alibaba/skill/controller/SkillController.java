package org.happyhai.springai.alibaba.skill.controller;

import org.happyhai.springai.alibaba.skill.service.SkillAgentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Skill REST Controller
 */
@RestController
@RequestMapping("/skill")
public class SkillController {

    private final SkillAgentService skillAgentService;

    public SkillController(SkillAgentService skillAgentService) {
        this.skillAgentService = skillAgentService;
    }

    /**
     * Agent 对话
     */
    @GetMapping("/agent/chat")
    public ResponseEntity<String> agentChatGet(@RequestParam String question) {
        return ResponseEntity.ok(skillAgentService.chat(question));
    }
}