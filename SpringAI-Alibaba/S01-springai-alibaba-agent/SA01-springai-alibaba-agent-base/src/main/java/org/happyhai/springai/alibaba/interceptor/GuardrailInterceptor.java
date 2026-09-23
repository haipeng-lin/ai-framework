package org.happyhai.springai.alibaba.interceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

// ModelInterceptor - 内容安全检查
public class GuardrailInterceptor extends ModelInterceptor {

    private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
      // 前置：检查输入
      if (containsSensitiveContent(request.getMessages())) {
      	return ModelResponse.of(AssistantMessage.builder().content("检测到不适当的内容").build());
      }

      // 执行调用
      ModelResponse response = handler.call(request);

      // 后置：检查输出
      return sanitizeIfNeeded(response);
  }

    private ModelResponse sanitizeIfNeeded(ModelResponse response) {
        try {
            AssistantMessage message = (AssistantMessage) response.getMessage();
            String textContent = message.getText();

            // 解析JSON提取content字段
            JsonNode jsonNode = objectMapper.readTree(textContent);
            String content = jsonNode.has("content") ? jsonNode.get("content").asText() : textContent;

            // 敏感词过滤
            String sanitized = content.replaceAll("敏感词", "***");
            return ModelResponse.of(AssistantMessage.builder().content(sanitized).build());
        } catch (Exception e) {
            return response;
        }
    }

    private boolean containsSensitiveContent(List<Message> messages) {
        // 检查消息中是否包含敏感内容
        for (Message message : messages) {
            String content = message.getText();
            if (content != null && content.contains("中国共产党")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getName() {
        return "";
    }
}