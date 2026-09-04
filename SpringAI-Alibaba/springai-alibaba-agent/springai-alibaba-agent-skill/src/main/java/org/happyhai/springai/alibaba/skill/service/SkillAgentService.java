package org.happyhai.springai.alibaba.skill.service;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Skill Agent 服务
 * <p>
 * 基于 ReactAgent 实现渐进式披露机制
 */
@Service
public class SkillAgentService {

    private static final Logger logger = LoggerFactory.getLogger(SkillAgentService.class);

    private final ReactAgent agent;

    public SkillAgentService(ReactAgent agent) {
        this.agent = agent;
    }

    public String chat(String question) {
        logger.info("收到问题: {}", question);
        try {
            AssistantMessage call = agent.call(question);
            String text = call.getText();
            logger.info("Agent 回答: {}", text);
            return text;
        } catch (Exception e) {
            logger.error("Agent 执行失败: {}", e.getMessage(), e);
            return "处理失败: " + e.getMessage();
        }
    }
}