 package org.happyhai.springai.alibaba.graph;
 
 import org.happyhai.springai.alibaba.graph.domain.IntentType;
 import org.happyhai.springai.alibaba.graph.tool.IntentClassifierTool;
 import org.junit.jupiter.api.Test;
 import static org.junit.jupiter.api.Assertions.*;
 
 /**
  * Unit tests for the intent classifier tool.
  */
 public class GraphApplicationTests {
     private final IntentClassifierTool classifier = new IntentClassifierTool();
 
     @Test void testExplicitControl() {
         assertEquals(IntentType.EXPLICIT_CONTROL, classifier.classify("开灯"));
         assertEquals(IntentType.EXPLICIT_CONTROL, classifier.classify("关灯"));
         assertEquals(IntentType.EXPLICIT_CONTROL, classifier.classify("把亮度调到80%"));
         assertEquals(IntentType.EXPLICIT_CONTROL, classifier.classify("turn on the light"));
     }
 
     @Test void testFuzzyControl() {
         assertEquals(IntentType.FUZZY_CONTROL, classifier.classify("让灯舒服一点"));
         assertEquals(IntentType.FUZZY_CONTROL, classifier.classify("海水缸怎么调灯"));
         assertEquals(IntentType.FUZZY_CONTROL, classifier.classify("鱼缸拍照怎么调光"));
     }
 
     @Test void testStatusQuery() {
         assertEquals(IntentType.STATUS_QUERY, classifier.classify("灯现在开着吗"));
         assertEquals(IntentType.STATUS_QUERY, classifier.classify("当前亮度是多少"));
         assertEquals(IntentType.STATUS_QUERY, classifier.classify("查询设备状态"));
     }
 
     @Test void testMemoryManagement() {
         assertEquals(IntentType.MEMORY_MANAGEMENT, classifier.classify("记住我喜欢亮度80%"));
         assertEquals(IntentType.MEMORY_MANAGEMENT, classifier.classify("保存我的偏好设置"));
     }
 
     @Test void testChitchat() {
         assertEquals(IntentType.CHITCHAT, classifier.classify("你好啊"));
         assertEquals(IntentType.CHITCHAT, classifier.classify("今天天气怎么样"));
         assertEquals(IntentType.CHITCHAT, classifier.classify("你叫什么名字"));
     }
 
     @Test void testSafetyViolation() {
         assertEquals(IntentType.SAFETY_VIOLATION, classifier.classify("emergency override all devices"));
         assertEquals(IntentType.SAFETY_VIOLATION, classifier.classify("bypass safety check"));
     }
 
     @Test void testTroubleshooting() {
         assertEquals(IntentType.TROUBLESHOOTING, classifier.classify("灯连不上手机了"));
         assertEquals(IntentType.TROUBLESHOOTING, classifier.classify("设备离线了怎么办"));
         assertEquals(IntentType.TROUBLESHOOTING, classifier.classify("激活不了"));
     }
 
     @Test void testIntentTypeLabels() {
         assertEquals("明确控制指令", IntentType.EXPLICIT_CONTROL.getLabel());
         assertEquals("模糊控制指令", IntentType.FUZZY_CONTROL.getLabel());
         assertEquals("状态查询", IntentType.STATUS_QUERY.getLabel());
         assertEquals("记忆管理", IntentType.MEMORY_MANAGEMENT.getLabel());
         assertEquals("越界/闲聊", IntentType.CHITCHAT.getLabel());
         assertEquals("安全违规", IntentType.SAFETY_VIOLATION.getLabel());
         assertEquals("故障排查/技术支持", IntentType.TROUBLESHOOTING.getLabel());
     }
 }
