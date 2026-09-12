 package org.happyhai.springai.alibaba.graph.config;
 
 import org.happyhai.springai.alibaba.graph.tool.*;
 import org.springframework.ai.tool.ToolCallback;
 import org.springframework.context.annotation.Bean;
 import org.springframework.context.annotation.Configuration;
 
 /**
  * Tool callback bean definitions.
  * All 10 tools (1 router + 9 specialized) are registered here.
  */
 @Configuration
 public class ToolCallbackConfig {
 
     @Bean public ToolCallback intentClassifier()      { return IntentClassifierTool.create(); }
     @Bean public ToolCallback safetyValidation()     { return SafetyValidationTool.create(); }
     @Bean public ToolCallback hardwareExecution()    { return HardwareExecutionTool.create(); }
     @Bean public ToolCallback agentReasoning()       { return AgentReasoningTool.create(); }
     @Bean public ToolCallback statusQuery()          { return StatusQueryTool.create(); }
     @Bean public ToolCallback memoryManagement()     { return MemoryManagementTool.create(); }
     @Bean public ToolCallback chitchatRejection()   { return ChitchatRejectionTool.create(); }
     @Bean public ToolCallback safetyInterception()  { return SafetyInterceptionTool.create(); }
     @Bean public ToolCallback troubleshooting()      { return TroubleshootingTool.create(); }
 }
