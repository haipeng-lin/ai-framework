package org.happyhai.springai.alibaba.light.agent.tools;

import org.happyhai.springai.alibaba.light.agent.domain.FaultSymptomRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.BiFunction;

public class DiagnoseLightIssueTool implements BiFunction<FaultSymptomRequest, ToolContext, String> {

    private static final Logger log = LoggerFactory.getLogger(DiagnoseLightIssueTool.class);

    @Override
    public String apply(FaultSymptomRequest request, ToolContext context) {
        String symptom = request.getSymptom();
        log.info("诊断灯光异常: {}", symptom);
        return String.format(
                "根据描述【%s】，可能的故障原因：\n" +
                "1. 电源供电不稳定，建议检查电源适配器\n" +
                "2. LED 灯珠老化，可观察发光是否均匀\n" +
                "3. 驱动板过热保护，建议断电冷却后重试\n" +
                "4. 设备连接线松动，建议检查接线",
                symptom
        );
    }

    public static FunctionToolCallback create() {
        return FunctionToolCallback.builder("diagnose_light_issue", new DiagnoseLightIssueTool())
                .description("诊断灯光异常，参数 symptom 描述用户观察到的异常现象，返回可能的故障原因列表")
                .inputType(FaultSymptomRequest.class)
                .build();
    }
}
