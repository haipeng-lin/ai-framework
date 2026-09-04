package org.happyhai.springai.alibaba.controller;

import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;
 
import java.util.UUID;
 
/**
 * Redis持久化多轮对话接口
 */
@RestController
@RequestMapping("/memory")
public class ChatMemoryController {
 
    @Resource(name = "qwenChatClient")
    private ChatClient qwenChatClient;
 
    /**
     * 带会话隔离的多轮对话接口
     */
    @GetMapping("/chat")
    public String chat(
            @RequestParam(name = "msg") String msg,
            @RequestParam(name = "userId", required = false) String userId) {

        // 2. 生成/获取会话ID：如果传入userId则使用userId，否则生成随机UUID
        String conversationId = (userId == null || userId.isBlank()) 
                ? UUID.randomUUID().toString() 
                : userId.trim();
 
        // 3. 调用ChatClient：通过advisors传入会话ID，实现会话隔离
        return qwenChatClient
                .prompt(msg)
                // 核心：传入CONVERSATION_ID参数
                .advisors(advisorSpec -> advisorSpec.param(CONVERSATION_ID, conversationId))
                .call()
                .content();
    }
}