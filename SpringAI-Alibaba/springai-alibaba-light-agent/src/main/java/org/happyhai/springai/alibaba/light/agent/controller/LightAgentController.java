package org.happyhai.springai.alibaba.light.agent.controller;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.streaming.GraphFlux;
import com.alibaba.cloud.ai.graph.streaming.ParallelGraphFlux;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/light-agent")
public class LightAgentController {

    private static final Logger log = LoggerFactory.getLogger(LightAgentController.class);

    private final CompiledGraph lightAgentGraph;

    public LightAgentController(CompiledGraph lightAgentGraph) {
        this.lightAgentGraph = lightAgentGraph;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestParam String query) {
        RunnableConfig config = RunnableConfig.builder()
                .threadId("light-agent-" + System.currentTimeMillis())
                .build();

        return lightAgentGraph.graphResponseStream(Map.of("user_input", query), config)
                .filter(resp -> !resp.isError())
                .flatMap(resp -> {
                    CompletableFuture<NodeOutput> outputFuture = resp.getOutput();
                    if (outputFuture == null) {
                        return Flux.empty();
                    }
                    NodeOutput output;
                    try {
                        output = outputFuture.join();
                    } catch (Exception e) {
                        log.warn("Failed to get output from GraphResponse", e);
                        return Flux.empty();
                    }
                    if (output == null) {
                        return Flux.empty();
                    }

                    Object stateObj = output.state().value("intent_stream").orElse(null);
                    if (stateObj instanceof ParallelGraphFlux pgf) {
                        List<GraphFlux<?>> fluxes = pgf.getGraphFluxes();
                        if (fluxes == null || fluxes.isEmpty()) return Flux.empty();
                        List<Flux<String>> tokenFluxes = fluxes.stream()
                                .map(gf -> {
                                    Flux<Object> rawFlux = (Flux<Object>) gf.getFlux();
                                    Function<Object, String> chunkFn = gf.getChunkResult();
                                    return rawFlux
                                            .<String>map(item -> chunkFn.apply(item))
                                            .filter(c -> c != null && !c.isEmpty());
                                })
                                .collect(Collectors.toList());
                        return Flux.merge(tokenFluxes);
                    }

                    String nodeName = output.node();
                    String msg = output.state().value("messages")
                            .orElseGet(() -> output.state().value("device_command").orElse(""))
                            .toString();
                    return Flux.just("[node:" + nodeName + "] " + msg);
                })
                .timeout(Duration.ofSeconds(60))
                .onErrorResume(e -> {
                    log.error("Stream error", e);
                    return Flux.just("[error] " + e.getMessage());
                });
    }
}
