package org.happyhai.agentscope.hello;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Paths;

@SpringBootApplication
public class A01AgentscopeHelloApplication {

    public static void main(String[] args) {
        SpringApplication.run(A01AgentscopeHelloApplication.class, args);

            HarnessAgent agent = HarnessAgent.builder()
                    .name("note-taker")
                    .sysPrompt("你是一个帮助用户做笔记的助手。")
                    .model("dashscope:qwen-plus")
                    .workspace(Paths.get(".agentscope/workspace"))
                    .compaction(CompactionConfig.builder()
                            .triggerMessages(30)
                            .keepMessages(10)
                            .build())
                    .build();

            RuntimeContext ctx = RuntimeContext.builder()
                    .sessionId("demo-session")
                    .userId("alice")
                    .build();

            // 第一轮：自我介绍 + 当天的事
            agent.call(new UserMessage("我叫天宇，今天准备一个关于 ReAct 的技术分享。"), ctx).block();

            // 第二轮：同 sessionId，自动恢复上一轮状态后回答
            agent.call(new UserMessage("我叫什么？我今天要干什么？"), ctx).block();
    }

}
