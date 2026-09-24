package org.happyhai.agentscope.permission.config;

import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

@Configuration
public class AgentConfig {

    @Bean(destroyMethod = "close")
    public HarnessAgent permissionedAgent(DashScopeChatModel chatModel,
                                         Toolkit toolkit,
                                         AgentStateStore stateStore) {
        PermissionContextState permCtx = PermissionContextState.builder()
                .mode(PermissionMode.DEFAULT)
                // DEFAULT 模式下没有显式 ALLOW 规则的工具会被默认 ASK 拦下，
                // 因此把只读类工具 opt-in 放行。
                .addAllowRule(
                        "query_online_devices",
                        new PermissionRule(
                                "query_online_devices", null,
                                PermissionBehavior.ALLOW, "policy"))
                // 物理动作必须人工确认。
                .addAskRule(
                        "toggle_light",
                        new PermissionRule(
                                "toggle_light", null, PermissionBehavior.ASK, "policy"))
                .build();

        String sep = String.format("%n%n");
        String sysPrompt =
                "你是一位智能家居管家，负责管理家里的灯光、空调等设备。" + sep +
                "=== 重要工作流：操作灯时必须先查后做 ===" + sep +
                "当用户说『关灯 / 开灯 / 把灯打开 / 关掉灯』之类需要操作灯的请求时，遵循以下流程：" + sep +
                "1. 不要立刻调 toggle_light。先调 query_online_devices(userId) 拿到该用户当前在线的设备列表，" +
                "工具会返回 [{deviceId, type, name}, ...] 形式的 JSON 字符串。" + sep +
                "2. 把拿到的设备列表摆给用户，让他挑选要操作的设备（说明支持多选），例如：" + sep +
                "   你家这几盏灯在线：" + sep +
                "   - dev-001  客厅灯" + sep +
                "   - dev-002  卧室灯" + sep +
                "   - dev-003  ...（非灯跳过）" + sep +
                "   请告诉我要关哪些灯（可以多选，写 deviceId 或名字都行）。" + sep +
        "3. 用户给出选择之后，**立即**对每一个被选中的设备调一次 toggle_light(deviceId, action)；" +
                "不要先用文字描述『接下来要 ...』，不要预告，直接发起工具调用。" + sep +
                "   - deviceId 必须是 query_online_devices 返回的，禁止编造。" + sep +
                "   - action 用户说关就用 off，说开就用 on。" + sep +
                "4. toggle_light 是物理动作，permission 系统会在调用发生时自动暂停等用户审核，" +
                "把『接下来要关/开什么』之类的铺垫话留给框架的 approval_required 事件带给前端，" +
                "你自己只需要保证工具调用真的发出了就行。" + sep +
                "违反规则：跳过 query_online_devices 直接调 toggle_light 是禁止的；" +
                "凭空写出 deviceId 也是禁止的；调用 tool 时不要用自然语言铺垫。" + sep +
                "如果用户问的不是操控灯，那么只用 query_online_devices 帮他看设备就够了，不要自动调 toggle_light。";

        return HarnessAgent.builder()
                .name("permissioned-agent")
                .sysPrompt(sysPrompt)
                .model(chatModel)
                .toolkit(toolkit)
                .stateStore(stateStore)
                .permissionContext(permCtx)
                .workspace(Paths.get(".agentscope/workspace"))
                .compaction(CompactionConfig.builder()
                        .triggerMessages(30)
                        .keepMessages(10)
                        .build())
                .build();
    }

}
