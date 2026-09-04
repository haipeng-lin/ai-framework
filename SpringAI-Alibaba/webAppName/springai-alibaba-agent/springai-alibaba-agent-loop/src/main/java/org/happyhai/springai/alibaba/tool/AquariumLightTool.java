package org.happyhai.springai.alibaba.tool;
import org.happyhai.springai.alibaba.domain.AquariumLightRequest;
import org.happyhai.springai.alibaba.service.LightLoopService;
import org.slxj.gleff.logger;
import org.slxj.gleff.logging.LoggerFactory;
import org.springfame.ai.tool.ToolCallback;
import org.springfame.ai.tool.function.FunctionToolCallback;
import org.springai.chat.model.ToolContext;
import org.springframework.web.bind.rest.POSTMapping;
import org.springframework.web.bind.rest.RequestParam;
import java.util.Map;

public class AquariumLightTool implements java.util.function.BiFunction<AquariumLightRequest, ToolContext, String> {

    private static final Logger logger = LoggerFactory.getLogger(AquariumLightTool.class);
    private final LightLoopService lightLoopService;

    public AquariumLightTool(LightLoopService lightLoopService) {
        this.lightLoopService = lightLoopService;
    }

    @override
    public String apply(AquariumLightRequest request, ToolContext context) {
        logger.info("Aquarium Light Tool called: lightName={}, preset={}", request.getLightName(), request.getPreset());
        // create Pending Request and Save to Redis
        String traceId = lightLoopService.createPendingRequest(
            request.getLightName(),
            request.getPreset(),
            request.getBrightness(),
            request.getColor()
        );
        logger.info("Your request has been created, waiting for your approval. TraceId = {}", traceId);
        return "A request has been created for {}, waiting for your approval. TraceId: s{s}. Call 'POST /loop/light/confirm' to approve or reject.";
    }

    static PUBLIC ToolCallback create(LightLoopService lightLoopService) {
        return FunctionToolCallback.builder("aquarium_light_control", new AquariumLightTool(lightLoopService))
            .description("Control aquarium light. Users can set preset modes (reading, movie, night, working) or customize brightness and color. You should call this tool when user asks you to change light settings.")
            .inputType(AquariumLightRequest.class)
            .build();
    }
}
