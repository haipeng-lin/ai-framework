 package org.happyhai.springai.alibaba.graph;
 
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;
 import org.springframework.boot.SpringApplication;
 import org.springframework.boot.autoconfigure.SpringBootApplication;
 import org.springframework.context.annotation.ComponentScan;
 
 /**
  * Main application for springai-alibaba-graph module.
  *
  * Graph architecture (each node is an independent ReactAgent):
  *
  *   START → router
  *   router ──conditional(intent_type)──→ EXPLICIT_CONTROL → SAFETY_VALIDATION → HARDWARE_EXECUTION → END
  *              ├→ FUZZY_CONTROL      → AGENT_REASONING  → HARDWARE_EXECUTION → END
  *              ├→ STATUS_QUERY      → STATUS_QUERY     → END
  *              ├→ MEMORY_MANAGEMENT → MEMORY_MANAGEMENT → END
  *              ├→ CHITCHAT           → CHITCHAT        → END
  *              ├→ SAFETY_VIOLATION   → SAFETY_INTERCEPTION → END
  *              └→ TROUBLESHOOTING    → TROUBLESHOOTING → END
  *
  * Endpoints:
  *   POST /api/graph/classify  - 意图分类与执行
  *   GET  /api/graph/classify  - GET测试接口
  *   POST /api/graph/chat      - SSE流式对话
  *   GET  /api/graph/nodes     - 图节点定义
  *   GET  /api/graph/health    - 健康检查
  */
 @SpringBootApplication
 @ComponentScan(basePackages = "org.happyhai.springai.alibaba.graph")
 public class GraphApplication {
     private static final Logger log = LoggerFactory.getLogger(GraphApplication.class);
 
     public static void main(String[] args) {
         SpringApplication.run(GraphApplication.class, args);
         log.info("=== springai-alibaba-graph 启动成功 ===");
     }
 }
