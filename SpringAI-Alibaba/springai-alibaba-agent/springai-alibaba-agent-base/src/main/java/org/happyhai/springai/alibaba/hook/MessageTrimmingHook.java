package org.happyhai.springai.alibaba.hook;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

@HookPositions({HookPosition.BEFORE_MODEL})
public class MessageTrimmingHook extends MessagesModelHook {
  private static final int MAX_MESSAGES = 10;

  @Override
  public String getName() {
      return "message_trimming";
  }

  @Override
  public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
      if (previousMessages.size() > MAX_MESSAGES) {
          // 只保留最后 MAX_MESSAGES 条消息
          List<Message> trimmedMessages = previousMessages.subList(
              previousMessages.size() - MAX_MESSAGES, 
              previousMessages.size()
          );
          return new AgentCommand(trimmedMessages, UpdatePolicy.REPLACE);
      }
      // 消息数量未超过限制，直接返回原消息列表
      return new AgentCommand(previousMessages);
  }
}