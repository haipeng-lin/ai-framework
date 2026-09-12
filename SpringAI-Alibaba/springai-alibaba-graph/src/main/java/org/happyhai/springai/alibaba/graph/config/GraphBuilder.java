 package org.happyhai.springai.alibaba.graph.config;
 
 import com.alibaba.cloud.ai.graph.*;
 import com.alibaba.cloud.ai.graph.action.*;
 import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
 import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;
 import org.springframework.ai.chat.client.ChatClient;
 import org.springframework.ai.chat.model.ChatModel;
 import org.springframework.ai.tool.ToolCallback;
 import org.springframework.stereotype.Component;
 import jakarta.annotation.PostConstruct;
import org.happyhai.springai.alibaba.graph.domain.IntentType;
 import java.util.*;
 
 /**
  * Builds the aquarium light intent classification StateGraph.
  *
  * Graph structure:
  *
  *   START ──→ router ──conditional(intent_type)──────────────────┐
  *       ├→ EXPLICIT_CONTROL ──────────────────────────────────┤
  *       ├→ FUZZY_CONTROL  ──────────────────────────────────┤
  *       ├→ STATUS_QUERY ─────────────────────────────────────┤
  *       ├→ MEMORY_MANAGEMENT ────────────────────────────────┤
  *       ├→ CHITCHAT ───────────────────────────────────────┤
  *       ├→ SAFETY_VIOLATION ───────────────────────────────┤
  *       └→ TROUBLESHOOTING ────────────────────────────────┘
  *
  * Each node (except router) is a ReactAgent with its own system prompt and tools.
  * The router node uses AsyncCommandAction to classify intent AND route in one step.
  */
 @Component
 public class GraphBuilder {
     private static final Logger log = LoggerFactory.getLogger(GraphBuilder.class);
 
     private final ChatModel chatModel;
     private final ToolCallback[] allTools;
     private CompiledGraph compiledGraph;
 
     public GraphBuilder(ChatModel chatModel,
                          ToolCallback intentClassifier,
                          ToolCallback safetyValidation,
                          ToolCallback hardwareExecution,
                          ToolCallback agentReasoning,
                          ToolCallback statusQuery,
                          ToolCallback memoryManagement,
                          ToolCallback chitchatRejection,
                          ToolCallback safetyInterception,
                          ToolCallback troubleshooting) {
         this.chatModel = chatModel;
         this.allTools = new ToolCallback[]{
                 intentClassifier, safetyValidation, hardwareExecution,
                 agentReasoning, statusQuery, memoryManagement,
                 chitchatRejection, safetyInterception, troubleshooting
         };
     }
 
     @PostConstruct
     public void build() {
         log.info("=== Building Aquarium Light Intent Graph ===");
         try {
             StateGraph graph = new StateGraph();
 
         // ── START ──────────────────────────────────────────────────────
         graph.addEdge(StateGraph.START, NodeNames.ROUTER);
 
         // ── Router Node ───────────────────────────────────────────────
         // The router uses AsyncCommandAction so it can both update state AND route.
         // It classifies intent, stores result in state, and returns Command(gotoNode).
         graph.addNode(NodeNames.ROUTER, AsyncCommandAction.node_async(routerCommandAction()), routerMappings());
 
         // ── Specialized Agent Nodes ────────────────────────────────────
         graph.addNode(NodeNames.EXPLICIT_CONTROL,   explicitControlAction());
         graph.addNode(NodeNames.FUZZY_CONTROL,     fuzzyControlAction());
         graph.addNode(NodeNames.STATUS_QUERY,       statusQueryAction());
         graph.addNode(NodeNames.MEMORY_MANAGEMENT,memoryManagementAction());
         graph.addNode(NodeNames.CHITCHAT,          chitchatAction());
         graph.addNode(NodeNames.SAFETY_VIOLATION,  safetyViolationAction());
         graph.addNode(NodeNames.TROUBLESHOOTING,   troubleshootingAction());
 
         // ── Terminal Edges ────────────────────────────────────────────
         graph.addEdge(NodeNames.EXPLICIT_CONTROL,    StateGraph.END);
         graph.addEdge(NodeNames.FUZZY_CONTROL,       StateGraph.END);
         graph.addEdge(NodeNames.STATUS_QUERY,       StateGraph.END);
         graph.addEdge(NodeNames.MEMORY_MANAGEMENT, StateGraph.END);
         graph.addEdge(NodeNames.CHITCHAT,           StateGraph.END);
         graph.addEdge(NodeNames.SAFETY_VIOLATION,  StateGraph.END);
         graph.addEdge(NodeNames.TROUBLESHOOTING,   StateGraph.END);
 
         // ── Compile ───────────────────────────────────────────────────
         SaverConfig saverConfig = SaverConfig.builder()
                 .register(MemorySaver.builder().build())
                 .build();
         this.compiledGraph = graph.compile(
                 CompileConfig.builder()
                         .recursionLimit(30)
                         .saverConfig(saverConfig)
                         .build()
         );
 
         log.info("=== Graph compiled successfully: {} nodes ===", NodeNames.ALL.length);
     }
 
     // ── Router ──────────────────────────────────────────────────────────────────────
     // Router CommandAction: classifies intent, stores in state, returns Command(gotoNode).
     private CommandAction routerCommandAction() {
         return (state, config) -> {
             log.info("[Router] Running...");
             String message = extractMessage(state);
             String userId  = str(state, "user_id", "guest");
             String deviceId = str(state, "device_id", "");
 
             ChatClient client = ChatClient.builder(chatModel).build();
             String prompt = String.format(
                     "用户ID: %s | 设备ID: %s | 用户消息: %s\n" +
                     "请使用 intent_classifier 工具分析用户消息的意图类型，返回intentType（如EXPLICIT_CONTROL）和intentLabel。",
                     userId, deviceId, message);
 
             String response = client.prompt()
                     .system(NodePrompt.ROUTER)
                     .user(prompt)
                     .call()
                     .content();
 
             String intentType = parseIntentType(response);
             String intentLabel = parseIntentLabel(response);
 
             log.info("[Router] → {} ({})", intentType, intentLabel);
 
             Map<String, Object> updates = new LinkedHashMap<>();
             updates.put("intent_type", intentType);
             updates.put("intent_label", intentLabel);
             updates.put("router_output", response);
 
             // Return Command with gotoNode = the intent type name (maps to node)
             return new Command(intentType, updates);
         };
     }
 
     private Map<String, String> routerMappings() {
         Map<String, String> m = new LinkedHashMap<>();
         m.put("EXPLICIT_CONTROL",    NodeNames.EXPLICIT_CONTROL);
         m.put("FUZZY_CONTROL",      NodeNames.FUZZY_CONTROL);
         m.put("STATUS_QUERY",        NodeNames.STATUS_QUERY);
         m.put("MEMORY_MANAGEMENT",  NodeNames.MEMORY_MANAGEMENT);
         m.put("CHITCHAT",           NodeNames.CHITCHAT);
         m.put("SAFETY_VIOLATION",    NodeNames.SAFETY_VIOLATION);
         m.put("TROUBLESHOOTING",    NodeNames.TROUBLESHOOTING);
         return m;
     }
 
     // ── Specialized Node Actions (each is an independent ReactAgent) ──────────────────────
 
     private AsyncNodeAction explicitControlAction() {
         return agentNodeAction(NodeNames.EXPLICIT_CONTROL,
                 "水族灯明确控制节点。处理明确的控制指令（开灯/关灯/亮度调节）。",
                 "safety_validation", "hardware_execution");
     }
 
     private AsyncNodeAction fuzzyControlAction() {
         return agentNodeAction(NodeNames.FUZZY_CONTROL,
                 "水族灯模糊控制推理节点。处理模糊指令（让灯舒服/根据鱼种调节）。",
                 "agent_reasoning", "hardware_execution");
     }
 
     private AsyncNodeAction statusQueryAction() {
         return agentNodeAction(NodeNames.STATUS_QUERY,
                 "水族灯状态查询节点。查询设备电源/亮度/色温/网络状态。",
                 "status_query");
     }
 
     private AsyncNodeAction memoryManagementAction() {
         return agentNodeAction(NodeNames.MEMORY_MANAGEMENT,
                 "水族灯记忆管理节点。保存或查询用户偏好（save/recall/list/delete）。",
                 "memory_management");
     }
 
     private AsyncNodeAction chitchatAction() {
         return agentNodeAction(NodeNames.CHITCHAT,
                 "水族灯闲聊拒答节点。礼貌拒绝闲聊和无关话题，引导回水族灯控制。",
                 "chitchat_rejection");
     }
 
     private AsyncNodeAction safetyViolationAction() {
         return agentNodeAction(NodeNames.SAFETY_VIOLATION,
                 "水族灯安全拦截节点。严肃拦截危险命令（emergency/bypass/广播控制）。",
                 "safety_interception");
     }
 
     private AsyncNodeAction troubleshootingAction() {
         return agentNodeAction(NodeNames.TROUBLESHOOTING,
                 "水族灯故障排查节点。诊断设备离线/连接失败/MQTT断开等问题。",
                 "troubleshooting");
     }
 
     /**
      * Builds an AsyncNodeAction that wraps a ReactAgent call with specific tools.
      */
     private AsyncNodeAction agentNodeAction(String nodeName, String description, String... toolNames) {
         Set<String> nameSet = new HashSet<>(Arrays.asList(toolNames));
         ToolCallback[] nodeTools = Arrays.stream(allTools)
                 .filter(t -> nameSet.contains(t.getToolDefinition().name()))
                 .toArray(ToolCallback[]::new);
 
         return state -> {
             log.info("[{}] Running...", nodeName);
             String message = extractMessage(state);
             String userId  = str(state, "user_id", "guest");
             String deviceId = str(state, "device_id", "");
 
             String prompt = String.format(
                     "用户消息: %s\n用户ID: %s\n设备ID: %s\n" +
                     "已识别意图: %s\n请执行对应操作。",
                     message, userId, deviceId, str(state, "intent_type", "未知"));
 
             ChatClient client = ChatClient.builder(chatModel).build();
             String response;
             if (nodeTools.length > 0) {
                 response = client.prompt()
                         .system(NodePrompt.forAgent(nodeName, description))
                         .user(prompt)
                         .tools(nodeTools)
                         .call()
                         .content();
             } else {
                 response = client.prompt()
                         .system(NodePrompt.forAgent(nodeName, description))
                         .user(prompt)
                         .call()
                         .content();
             }
 
             log.info("[{}] Output:\n{}", nodeName, response);
 
             Map<String, Object> updates = new LinkedHashMap<>();
             updates.put("result_message", response != null ? response : "");
             updates.put("last_node", nodeName);
             return java.util.concurrent.CompletableFuture.completedFuture(updates);
         };
     }
 
     // ── Helpers ────────────────────────────────────────────────────────────────────────
     private String extractMessage(OverAllState state) {
         Object msgObj = state.value("messages").orElse(null);
         if (msgObj instanceof List<?> list && !list.isEmpty()) {
             return list.get(0).toString();
         }
         return str(state, "user_message", "");
     }
 
     private String str(OverAllState state, String key, String fallback) {
         return state.value(key).map(Object::toString).orElse(fallback);
     }
 
     private String parseIntentType(String response) {
         if (response == null) return "CHITCHAT";
         for (String type : new String[]{
                 "EXPLICIT_CONTROL", "FUZZY_CONTROL", "STATUS_QUERY",
                 "MEMORY_MANAGEMENT", "CHITCHAT", "SAFETY_VIOLATION", "TROUBLESHOOTING"
         }) {
             if (response.contains(type)) return type;
         }
         String lower = response.toLowerCase();
         if (lower.contains("explicit") || lower.contains("控制") || lower.matches(".*[开关].*"))
             return "EXPLICIT_CONTROL";
         if (lower.contains("fuzzy") || lower.contains("模糊") || lower.contains("舒服") || lower.contains("鱼"))
             return "FUZZY_CONTROL";
         if (lower.contains("status") || lower.contains("状态") || lower.contains("查询") || lower.contains("开着"))
             return "STATUS_QUERY";
         if (lower.contains("memory") || lower.contains("记忆") || lower.contains("保存") || lower.contains("偏好"))
             return "MEMORY_MANAGEMENT";
         if (lower.contains("safety") || lower.contains("安全") || lower.contains("violation"))
             return "SAFETY_VIOLATION";
         if (lower.contains("troubleshoot") || lower.contains("故障") || lower.contains("排查") || lower.contains("离线"))
             return "TROUBLESHOOTING";
         return "CHITCHAT";
     }
 
     private String parseIntentLabel(String response) {
         if (response == null) return "未知";
         for (var t : IntentType.values()) {
             if (response.contains(t.name())) return t.getLabel();
         }
         return "未知";
     }
 
     public CompiledGraph getCompiledGraph() { return compiledGraph; }
 }
