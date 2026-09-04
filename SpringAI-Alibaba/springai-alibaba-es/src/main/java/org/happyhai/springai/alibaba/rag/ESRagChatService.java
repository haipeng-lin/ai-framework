package org.happyhai.springai.alibaba.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RAG 问答服务
 * 基于混合检索结果进行问答
 */
@Service
public class ESRagChatService {

    private static final Logger logger = LoggerFactory.getLogger(ESRagChatService.class);

    private final ESHybridSearchService hybridSearchService;
    private final ChatClient dashScopeChatClient;

    public ESRagChatService(
            ESHybridSearchService hybridSearchService,
            @Qualifier("dashScopeChatClient") ChatClient dashScopeChatClient) {
        this.hybridSearchService = hybridSearchService;
        this.dashScopeChatClient = dashScopeChatClient;
    }

    /**
     * RAG 问答
     */
    public ChatResponse chat(ChatRequest request) {
        List<SearchResult> searchResults = hybridSearchService.hybridSearch(
                request.getQuestion(),
                request.getTopK(),
                request.getBm25Weight(),
                request.getKnnWeight(),
                request.getThreshold()
        );

        if (searchResults.isEmpty()) {
            ChatResponse response = new ChatResponse();
            response.setQuestion(request.getQuestion());
            response.setAnswer("抱歉，我在知识库中没有找到与您问题相关的内容。请尝试调整问题或添加更多相关文档。");
            response.setSources(List.of());
            return response;
        }

        String context = buildContext(searchResults);
        String prompt = buildPrompt(request.getQuestion(), context);
        String answer = callLlm(prompt);

        ChatResponse response = new ChatResponse();
        response.setQuestion(request.getQuestion());
        response.setAnswer(answer);
        response.setSources(searchResults);
        response.setModel("qwen-turbo");

        logger.info("RAG 问答完成，问题: {}, 检索到 {} 条结果", request.getQuestion(), searchResults.size());
        return response;
    }

    public ChatResponse chat(String question) {
        return chat(new ChatRequest(question));
    }

    private String buildContext(List<SearchResult> searchResults) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < searchResults.size(); i++) {
            SearchResult result = searchResults.get(i);
            context.append(String.format("【文档 %d】来源: %s\n%s\n\n",
                    i + 1,
                    result.getFileName(),
                    result.getContent()));
        }
        return context.toString();
    }

    private String buildPrompt(String question, String context) {
        return String.format("""
                你是一个专业的问答助手。请根据提供的上下文来回答用户的问题。

                要求：
                1. 只根据上下文中的信息进行回答，不要编造答案
                2. 如果上下文中没有相关信息，请如实告知用户
                3. 回答要准确、简洁、有条理
                4. 如果有多条相关信息，请综合整理后回答

                上下文：
                %s

                用户问题：%s

                请回答：
                """, context, question);
    }

    private String callLlm(String prompt) {
        try {
            return dashScopeChatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            logger.error("调用大模型失败: {}", e.getMessage(), e);
            return "抱歉，模型调用失败：" + e.getMessage();
        }
    }
}
