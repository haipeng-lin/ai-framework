package org.happyhai.springai.alibaba.light.agent.config;

import org.happyhai.springai.alibaba.light.agent.tools.*;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ToolConfig {

    @Bean
    public List<ToolCallback> controlTools() {
        return List.of(
                SetBrightnessTool.create(),
                SetColorTemperatureTool.create(),
                SetPresetTool.create()
        );
    }

    @Bean
    public List<ToolCallback> queryTools() {
        return List.of(
                QueryDeviceStatusTool.create(),
                QueryWaterTemperatureTool.create(),
                QueryLightIntensityTool.create(),
                DiagnoseLightIssueTool.create(),
                DiagnoseConnectivityTool.create()
        );
    }
}
