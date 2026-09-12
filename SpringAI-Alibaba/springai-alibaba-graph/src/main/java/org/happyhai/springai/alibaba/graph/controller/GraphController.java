 package org.happyhai.springai.alibaba.graph.controller;
 
 import com.alibaba.cloud.ai.graph.*;
 import com.fasterxml.jackson.core.JsonProcessingException;
 import com.fasterxml.jackson.databind.ObjectMapper;
 import org.happyhai.springai.alibaba.graph.config.GraphBuilder;
 import org.happyhai.springai.alibaba.graph.domain.GraphRequest;
 import org.happyhai.springai.alibaba.graph.domain.IntentType;
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;
 import org.springframework.http.MediaType;
 import org.springframework.web.bind.annotation.*;
 import reactor.core.publisher.Flux;
 import java.util.*;
 
 /**
  * REST controller for the graph-based intent classification demo.
  */
 @RestController
 @RequestMapping("/api/graph")
 public class GraphController {
     private static final Logger log = LoggerFactory.getLogger(GraphController.class);
     private static final ObjectMapper MAPPER = new ObjectMapper();
     private final GraphBuilder graphBuilder;
 
     public GraphController(GraphBuilder graphBuilder) {
         this.graphBuilder = graphBuilder;
     }
 
     private GraphRunner runner(GraphRequest req) {
         return new GraphRunner(graphBuilder.getCompiledGraph(),
                 RunnableConfig.builder().build());
     }
 
     /**
      * POST /api/graph/classify - Run user message through the intent graph.
      */
     @PostMapping("/classify")
     public Map<String, Object> classify(@RequestBody GraphRequest request) {
         log.info("=== Graph Request === userId={}, message={}, deviceId={}",
                 request.getUserId(), request.getMessage(), request.getDeviceId());
 
         OverAllState initialState = new OverAllState();
         initialState.input(Map.of(
                 "user_id",   request.getUserId() != null ? request.getUserId() : "guest",
                 "device_id", request.getDeviceId() != null ? request.getDeviceId() : "",
                 "messages",  List.of(request.getMessage() != null ? request.getMessage() : "")
         ));
 
         try {
             List<GraphResponse<NodeOutput>> outputs = runner(request).run(initialState)
                     .collectList().block();
 
             Map<String, Object> result = new LinkedHashMap<>();
             if (outputs != null && !outputs.isEmpty()) {
                 GraphResponse<NodeOutput> last = outputs.get(outputs.size() - 1);
                 if (last.resultValue().isPresent()) {
                     NodeOutput out = (NodeOutput) last.resultValue().get();
                     OverAllState state = out.state();
                     result.put("success", true);
                     result.put("node", out.node());
                     result.put("intent_type", state.value("intent_type").orElse("").toString());
                     result.put("intent_label", state.value("intent_label").orElse("").toString());
                     result.put("result_message", state.value("result_message").orElse("").toString());
                     log.info("Result - intent={}, node={}",
                             state.value("intent_type").orElse(""), out.node());
                 } else {
                     result.put("success", false);
                     result.put("message", "No result value in graph response");
                 }
             } else {
                 result.put("success", false);
                 result.put("message", "No output from graph");
             }
             return result;
         } catch (Exception e) {
             log.error("Graph execution failed", e);
             Map<String, Object> err = new LinkedHashMap<>();
             err.put("success", false);
             err.put("error", e.getMessage());
             return err;
         }
     }
 
     /**
      * GET /api/graph/classify - Simple GET test interface.
      */
     @GetMapping("/classify")
     public Map<String, Object> classifyGet(
             @RequestParam String message,
             @RequestParam(required = false, defaultValue = "guest") String userId,
             @RequestParam(required = false) String deviceId) {
         return classify(new GraphRequest(userId, message, deviceId, UUID.randomUUID().toString()));
     }
 
     /**
      * POST /api/graph/chat - SSE stream.
      */
     @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
     public Flux<String> chatStream(@RequestBody GraphRequest request) {
         log.info("=== SSE Chat === message={}", request.getMessage());
 
         OverAllState initialState = new OverAllState();
         initialState.input(Map.of(
                 "user_id",   request.getUserId() != null ? request.getUserId() : "guest",
                 "device_id", request.getDeviceId() != null ? request.getDeviceId() : "",
                 "messages",  List.of(request.getMessage() != null ? request.getMessage() : "")
         ));
 
         return runner(request).run(initialState)
                 .map(resp -> {
                     if (resp.resultValue().isPresent()) {
                         NodeOutput out = (NodeOutput) resp.resultValue().get();
                         return buildSse("node", Map.of(
                                 "node", out.node(),
                                 "isEnd", resp.isDone(),
                                 "intent_type", out.state().value("intent_type").orElse("").toString(),
                                 "result_message", out.state().value("result_message").orElse("").toString()
                         ));
                     }
                     return "";
                 })
                 .filter(s -> !s.isEmpty());
     }
 
     /**
      * GET /api/graph/nodes - Graph node definitions.
      */
     @GetMapping("/nodes")
     public Map<String, Object> getNodes() {
         return Map.of(
                 "graphName", "水族灯意图分类图",
                 "description", "基于图的智能水族灯意图分类与路由系统，每节点为独立ReactAgent",
                 "entryNode", "router",
                 "nodes", Arrays.stream(IntentType.values()).map(t -> Map.of(
                         "intentType", t.name(),
                         "intentLabel", t.getLabel(),
                         "priority", t.getPriority()
                 )).toList(),
                 "edges", List.of(
                         Map.of("from", "START", "to", "router", "type", "fixed"),
                         Map.of("from", "router", "to", "[conditional:intent_type]", "type", "conditional"),
                         Map.of("from", "explicit_control", "to", "END", "type", "terminal"),
                         Map.of("from", "fuzzy_control", "to", "END", "type", "terminal"),
                         Map.of("from", "status_query", "to", "END", "type", "terminal"),
                         Map.of("from", "memory_management", "to", "END", "type", "terminal"),
                         Map.of("from", "chitchat", "to", "END", "type", "terminal"),
                         Map.of("from", "safety_violation", "to", "END", "type", "terminal"),
                         Map.of("from", "troubleshooting", "to", "END", "type", "terminal")
                 )
         );
     }
 
     /**
      * GET /api/graph/health
      */
     @GetMapping("/health")
     public Map<String, Object> health() {
         return Map.of(
                 "status", "UP",
                 "module", "springai-alibaba-graph",
                 "totalNodes", 7,
                 "totalIntentTypes", IntentType.values().length
         );
     }
 
     private String buildSse(String eventName, Map<String, Object> data) {
         try {
             return "event: " + eventName + "\ndata: " + MAPPER.writeValueAsString(data) + "\n\n";
         } catch (JsonProcessingException e) {
             return "";
         }
     }
 }
