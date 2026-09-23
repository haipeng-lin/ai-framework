package org.happyhai.springai.alibaba.light.agent.workflow;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.alibaba.cloud.ai.graph.streaming.GraphFlux;
import com.alibaba.cloud.ai.graph.streaming.ParallelGraphFlux;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.alibaba.cloud.ai.graph.StateGraph.END;

@Configuration
public class LightWorkflowConfig {

    // ========== Intent Classifier ==========

    private static final String INTENT_CLASSIFIER_PROMPT =
            "你是一个水族灯控制系统的意图分类专家。请分析以下用户指令，判断它属于哪种类型，并只返回以下六个单词之一：\n" +
            "1. off_topic（越界闲聊：与水族灯控制无关的内容，如问候、聊天、天气等）\n" +
            "2. safety_violation（安全违规：涉及危险操作、非法请求、系统破解等）\n" +
            "3. clear_control（明确控制：清晰具体的控制指令，如[把亮度调到80]、[色温5000K]）\n" +
            "4. fuzzy_control（模糊控制：模糊的调节指令，如[亮一点]、[再暖一些]）\n" +
            "5. fault_diagnosis（故障排查：报告异常、请求诊断，如[灯不亮了]、[为什么闪烁]）\n" +
            "6. status_query（状态查询：查询当前状态、数据，如[现在水温多少]、[当前亮度]）\n\n" +
            "用户指令：%s\n\n只返回一个单词，不要加任何解释。";

    // ========== Node Prompts ==========

   private static final String CHITCHAT_PROMPT =
            "你是一个水族灯控制助手。用户发送了一条与水族灯控制无关的闲聊消息。\n" +
            "请用简短、友好的方式回应（1-2句话），然后自然地引导用户回到水族灯控制话题。\n" +
            "不要展开闲聊话题，不要讲笑话或故事，直接引导即可。\n" +
            "用户消息：%s";

    private static final String SAFETY_PROMPT =
            "你是一个严格的安全审核助手。检测到用户可能发送了以下内容：\n" +
            "[%s]\n\n" +
            "请判断这是否属于以下安全违规类型之一，并给出友好但坚定的提醒：\n" +
            "1. 要求关闭安全保护\n" +
            "2. 要求超频或超压操作\n" +
            "3. 要求破解系统或越权操作\n" +
            "4. 其他可能导致设备损坏或人身伤害的指令\n\n" +
            "如果属于违规，请明确告知不可执行并解释原因；如果不属于，请礼貌引导回正常话题。";

    private static final String CLEAR_CONTROL_PROMPT =
            "你是一个专业的水族灯控制助手。你的职责是执行用户明确的灯光控制指令。\n" +
            "可用控制工具：\n" +
            "- set_light_brightness(brightness: int) 亮度 0-100\n" +
            "- set_light_color_temperature(temperature: int) 色温 2700-6500K\n" +
            "- set_light_preset(mode: str) 预设模式：normal/plant/reef/night/sunrise/sunset\n" +
            "请根据用户指令调用合适的工具，完成控制后将执行结果告知用户。\n" +
            "用户指令：%s";

    private static final String FUZZY_CONTROL_PROMPT =
            "你是一个专业的水族灯控制助手。用户的指令比较模糊，需要你进行合理推断后再执行。\n" +
            "可用控制工具：\n" +
            "- set_light_brightness(brightness: int) 亮度 0-100\n" +
            "- set_light_color_temperature(temperature: int) 色温 2700-6500K\n" +
            "- set_light_preset(mode: str) 预设模式：normal/plant/reef/night/sunrise/sunset\n" +
            "规则：\n" +
            "- [亮一点] -> brightness +20；[暗一点] -> brightness -20\n" +
            "- [暖一些] -> temperature +500；[冷一些] -> temperature -500\n" +
            "- [夜间模式] -> preset=night；[草缸模式] -> preset=plant\n" +
            "根据模糊描述推断意图后执行，并在回复中说明你的推断过程。\n" +
            "用户指令：%s";

    private static final String FAULT_DIAGNOSIS_PROMPT =
            "你是一个专业的水族灯故障诊断助手。分析用户的故障描述，利用诊断工具排查问题。\n" +
            "可用诊断工具：\n" +
            "- diagnose_light_issue(symptom: str) 诊断灯光异常\n" +
            "- diagnose_connectivity() 诊断设备连接状态\n" +
            "- query_device_status() 查询设备状态\n" +
            "请先收集信息，然后给出诊断结论和解决建议。\n" +
            "用户描述：%s";

    private static final String STATUS_QUERY_PROMPT =
            "你是一个专业的水族灯状态查询助手。回答用户关于设备当前状态的查询。\n" +
            "可用查询工具：\n" +
            "- query_device_status() 查询完整设备状态\n" +
            "- query_water_temperature() 查询水温\n" +
            "- query_light_intensity() 查询光照强度\n" +
            "请调用合适的工具返回准确数据，并以友好方式呈现给用户。\n" +
            "用户查询：%s";

    // ========== KeyStrategy ==========

    private KeyStrategyFactory createKeyStrategyFactory() {
        return () -> {
            Map<String, KeyStrategy> strategies = new HashMap<>();
            strategies.put("user_input", new ReplaceStrategy());
            strategies.put("intent", new ReplaceStrategy());
            strategies.put("device_command", new ReplaceStrategy());
           strategies.put("messages", new ReplaceStrategy());
            strategies.put("intent_stream", new ReplaceStrategy());
            return strategies;
        };
    }

    // ========== Graph ==========

    @Bean
   public CompiledGraph lightAgentGraph(
            @Qualifier("chitchatChatClient") ChatClient chitchatChatClient,
            @Qualifier("safetyChatClient") ChatClient safetyChatClient,
            @Qualifier("controlReactAgent") ReactAgent controlReactAgent,
            @Qualifier("queryReactAgent") ReactAgent queryReactAgent) throws GraphStateException {

        // ---- Intent Classifier ----
        var intentClassifierNode = com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async(state -> {
            String input = state.value("user_input").orElse("").toString();
            String prompt = String.format(INTENT_CLASSIFIER_PROMPT, input);

            Flux<String> tokenFlux = chitchatChatClient.prompt().user(prompt).stream()
                    .content()
                    .filter(t -> !t.isEmpty());

            GraphFlux<String> gf = GraphFlux.of("intent_classifier", "intent_stream",
                    tokenFlux, Function.identity(), t -> t);
            ParallelGraphFlux pgf = ParallelGraphFlux.of(List.of(gf));

            String intent = chitchatChatClient.prompt(prompt).call().content().trim().toLowerCase();

            Map<String, Object> result = new HashMap<>();
            result.put("intent", intent);
            result.put("intent_stream", pgf);
            return result;
        });

        // ---- Node 1: Off-topic (ChatClient) ----
        var chitchatNode = com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async(state -> {
            String input = state.value("user_input").orElse("").toString();
            String reply = chitchatChatClient.prompt()
                    .system("你是一个友好的水族灯助手。")
                    .user(String.format(CHITCHAT_PROMPT, input))
                    .call()
                    .content();
            return Map.of("messages", reply);
        });

        // ---- Node 2: Safety violation (ChatClient) ----
        var safetyNode = com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async(state -> {
            String input = state.value("user_input").orElse("").toString();
            String reply = safetyChatClient.prompt()
                    .system("你是一个严格但友好的安全审核助手。")
                    .user(String.format(SAFETY_PROMPT, input))
                    .call()
                    .content();
            return Map.of("messages", reply);
        });

        // ---- Node 3: Fuzzy control (ReactAgent) ----
        var fuzzyControlNode = com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async(state -> {
            String input = state.value("user_input").orElse("").toString();
            String fullPrompt = String.format(FUZZY_CONTROL_PROMPT, input);
            RunnableConfig config = RunnableConfig.builder()
                    .threadId("light-agent-" + System.currentTimeMillis())
                    .build();
            String result = controlReactAgent.call(fullPrompt, config).getText();
            return Map.of("device_command", result, "messages", result);
        });

        // ---- Node 4: Clear control (ReactAgent) ----
        var clearControlNode = com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async(state -> {
            String input = state.value("user_input").orElse("").toString();
            String fullPrompt = String.format(CLEAR_CONTROL_PROMPT, input);
            RunnableConfig config = RunnableConfig.builder()
                    .threadId("light-agent-" + System.currentTimeMillis())
                    .build();
            String result = controlReactAgent.call(fullPrompt, config).getText();
            return Map.of("device_command", result, "messages", result);
        });

        // ---- Node 5: Fault diagnosis (ReactAgent) ----
        var faultDiagnosisNode = com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async(state -> {
            String input = state.value("user_input").orElse("").toString();
            String fullPrompt = String.format(FAULT_DIAGNOSIS_PROMPT, input);
            RunnableConfig config = RunnableConfig.builder()
                    .threadId("light-agent-" + System.currentTimeMillis())
                    .build();
            String result = queryReactAgent.call(fullPrompt, config).getText();
            return Map.of("messages", result);
        });

        // ---- Node 6: Status query (ReactAgent) ----
        var statusQueryNode = com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async(state -> {
            String input = state.value("user_input").orElse("").toString();
            String fullPrompt = String.format(STATUS_QUERY_PROMPT, input);
            RunnableConfig config = RunnableConfig.builder()
                    .threadId("light-agent-" + System.currentTimeMillis())
                    .build();
            String result = queryReactAgent.call(fullPrompt, config).getText();
            return Map.of("messages", result);
        });

        // ---- Build graph ----
        StateGraph graph = new StateGraph(createKeyStrategyFactory())
                .addNode("intent_classifier", intentClassifierNode)
                .addNode("off_topic", chitchatNode)
                .addNode("safety_violation", safetyNode)
                .addNode("fuzzy_control", fuzzyControlNode)
                .addNode("clear_control", clearControlNode)
                .addNode("fault_diagnosis", faultDiagnosisNode)
                .addNode("status_query", statusQueryNode)
                .addEdge(StateGraph.START, "intent_classifier")
                .addEdge("off_topic", END)
                .addEdge("safety_violation", END)
                .addEdge("fuzzy_control", END)
                .addEdge("clear_control", END)
                .addEdge("fault_diagnosis", END)
                .addEdge("status_query", END)
               .addConditionalEdges("intent_classifier",
                        com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async(state -> {
                            return state.value("intent").orElse("unknown").toString();
                        }),
                        Map.of(
                                "off_topic", "off_topic",
                                "safety_violation", "safety_violation",
                                "fuzzy_control", "fuzzy_control",
                                "clear_control", "clear_control",
                                "fault_diagnosis", "fault_diagnosis",
                                "status_query", "status_query"
                        )
                );

        return graph.compile();
    }
}
