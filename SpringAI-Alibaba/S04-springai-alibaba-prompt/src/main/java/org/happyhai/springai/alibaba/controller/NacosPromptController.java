package org.happyhai.springai.alibaba.controller;

import com.alibaba.cloud.ai.prompt.ConfigurablePromptTemplate;
import com.alibaba.cloud.ai.prompt.ConfigurablePromptTemplateFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/prompt")
public class NacosPromptController {

    private static final Logger logger = LoggerFactory.getLogger(NacosPromptController.class);

    private final ChatClient chatClient;
    private final ConfigurablePromptTemplateFactory promptTemplateFactory;

    public NacosPromptController(
            ChatModel chatModel,
            ConfigurablePromptTemplateFactory promptTemplateFactory
    ) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.promptTemplateFactory = promptTemplateFactory;
    }

    /**
     * 使用 Nacos 动态配置的 Prompt 示例
     *
     * @param authorName 作者名称，默认鲁迅
     * @return 流式返回的推荐书籍列表或作者介绍
     */
    @GetMapping(value = "/books")
    public Flux<String> generateBooks(
            @RequestParam(value = "author", required = false, defaultValue = "鲁迅") String authorName
    ) {
        // 第一个参数是模板名称（对应 Nacos 配置中的 name），第二个是默认模板
        ConfigurablePromptTemplate template = promptTemplateFactory.create(
                "author",
                "please list the three most famous books by this {author}."
        );
        Prompt prompt = template.create(Map.of("author", authorName));
        logger.info("最终构建的 prompt 为：{}", prompt.getContents());

        return chatClient.prompt(prompt)
                .stream()
                .content();
    }

    /**
     * 诗歌生成示例
     */
    @GetMapping(value = "/poem")
    public Flux<String> generatePoem(
            @RequestParam(value = "topic", required = false, defaultValue = "春天") String topic
    ) {
        // 第一个参数是模板名称（对应 Nacos 配置中的 name），第二个是默认模板
        ConfigurablePromptTemplate template = promptTemplateFactory.create(
                "poet",
                "请用七言绝句写一首关于{topic}的诗"
        );
        Prompt prompt = template.create(Map.of("topic", topic));
        logger.info("诗歌生成 prompt：{}", prompt.getContents());

        return chatClient.prompt(prompt)
                .stream()
                .content();
    }

    /**
     * 代码翻译示例
     */
    @GetMapping(value = "/translate")
    public Flux<String> translateCode(
            @RequestParam(value = "code", required = false, defaultValue = "def hello(): print('hello')") String code,
            @RequestParam(value = "targetLang", required = false, defaultValue = "Java") String targetLang
    ) {
        ConfigurablePromptTemplate template = promptTemplateFactory.create(
                "code-translator",
                "请将以下代码翻译成{targetLang}：{code}"
        );
        Prompt prompt = template.create(Map.of("code", code, "targetLang", targetLang));
        logger.info("代码翻译 prompt：{}", prompt.getContents());

        return chatClient.prompt(prompt)
                .stream()
                .content();
    }

    /**
     * 情感分析示例
     */
    @GetMapping(value = "/sentiment")
    public Flux<String> analyzeSentiment(
            @RequestParam(value = "text", required = false, defaultValue = "今天天气真好，心情很开心！") String text
    ) {
        ConfigurablePromptTemplate template = promptTemplateFactory.create(
                "sentiment",
                "请分析以下文本的情感倾向，只返回积极、消极或中性：{text}"
        );
        Prompt prompt = template.create(Map.of("text", text));
        logger.info("情感分析 prompt：{}", prompt.getContents());

        return chatClient.prompt(prompt)
                .stream()
                .content();
    }
}
