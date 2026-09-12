 package org.happyhai.springai.alibaba.graph.domain;
 
 public class GraphRequest {
     private String userId;
     private String message;
     private String deviceId;
     private String traceId;
 
     public GraphRequest() {}
     public GraphRequest(String userId, String message, String deviceId, String traceId) {
         this.userId = userId; this.message = message;
         this.deviceId = deviceId; this.traceId = traceId;
     }
     public String getUserId() { return userId; }
     public void setUserId(String userId) { this.userId = userId; }
     public String getMessage() { return message; }
     public void setMessage(String message) { this.message = message; }
     public String getDeviceId() { return deviceId; }
     public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
     public String getTraceId() { return traceId; }
     public void setTraceId(String traceId) { this.traceId = traceId; }
 }
