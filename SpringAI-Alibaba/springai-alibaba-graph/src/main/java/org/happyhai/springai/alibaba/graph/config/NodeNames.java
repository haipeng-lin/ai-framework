 package org.happyhai.springai.alibaba.graph.config;
 
 /**
  * Graph node name constants.
  */
 public final class NodeNames {
     private NodeNames() {}
 
     public static final String ROUTER            = "router";
     public static final String EXPLICIT_CONTROL   = "explicit_control";
     public static final String FUZZY_CONTROL     = "fuzzy_control";
     public static final String SAFETY_VALIDATION  = "safety_validation";
     public static final String HARDWARE_EXECUTION = "hardware_execution";
     public static final String AGENT_REASONING    = "agent_reasoning";
     public static final String STATUS_QUERY       = "status_query";
     public static final String MEMORY_MANAGEMENT  = "memory_management";
     public static final String CHITCHAT           = "chitchat";
     public static final String SAFETY_VIOLATION   = "safety_violation";
     public static final String TROUBLESHOOTING    = "troubleshooting";
 
     public static final String[] ALL = {
         ROUTER, EXPLICIT_CONTROL, FUZZY_CONTROL, SAFETY_VALIDATION,
         HARDWARE_EXECUTION, AGENT_REASONING, STATUS_QUERY,
         MEMORY_MANAGEMENT, CHITCHAT, SAFETY_VIOLATION, TROUBLESHOOTING
     };
 }
