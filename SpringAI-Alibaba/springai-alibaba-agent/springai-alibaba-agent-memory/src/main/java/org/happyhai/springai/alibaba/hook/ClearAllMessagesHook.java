package org.happyhai.springai.alibaba.hook;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * 删除所有消息 Hook - 清除整个消息历史
 */
@HookPositions({HookPosition.AFTER_MODEL})
public class ClearAllMessagesHook extends MessagesModelHook {

    @Override
    public String getName() {
        return "clear_all_messages";
    }

    @Override
    public AgentCommand afterModel(List<Message> previousMessages, RunnableConfig config) {
        // 删除所有消息，返回空列表
        return new AgentCommand(new ArrayList<>(), UpdatePolicy.REPLACE);
    }
}
