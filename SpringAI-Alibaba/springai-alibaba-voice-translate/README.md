# springai-alibaba-voice-translate

基于 Spring AI 的「实时语音翻译」Demo。后端把音频转写 + 翻译异步处理后，通过 Server-Sent Events (SSE) 把结果流式推回到前端页面。

## 功能概览

- **音频转写**：使用 SiliconFlow 上的 `FunAudioLLM/SenseVoiceSmall`（中英文为主）。
- **流式翻译**：使用 `Qwen/Qwen2.5-7B-Instruct`，按 token 片段实时回写到页面。
- **异步 + SSE**：上传后立刻返回 `taskId`，浏览器再开 SSE 连接拉数据，避免同步等待。
- **两种输入**：上传本地音频文件 / 浏览器调用麦克风直接录音。

## 整体流程

```
浏览器(Browser)           Controller                   AI 模型
   |                        |                            |
   |--- POST /auto/uploadAudio -----------------> |
   |                        |--- 存 taskId，启动异步线程     |
   |<----- { taskId } ------|                            |
   |                        |                            |
   |--- GET /auto/getResultStream/{taskId} ---> |
   |                        |--- 注册 SseEmitter         |
   |<== event: transcription ==|---------- SenseVoiceSmall
   |<== event: translation  ==|---------- Qwen (流式)
   |<== event: end =========|
```

## 目录结构

```
springai-alibaba-voice-translate/
├── pom.xml
└── src/main/
    ├── java/org/happyhai/springai/
    │   ├── VoiceTranslateDemoApplication.java        # Spring Boot 启动类
    │   └── alibaba/
    │       ├── controller/
    │       │   ├── AutoTranslateController.java      # 上传 + SSE 主流程
    │       │   ├── WebViewController.java            # 渲染 /translate 页面
    │       │   └── TranslationTask.java              # 内存任务记录
    │       └── service/
    │           └── AudioTranscriptionService.java    # 调用 TranscriptionModel
    └── resources/
        ├── application.yml                            # SiliconFlow 配置
        └── templates/translate.html                   # 前端页面（Thymeleaf）
```

## 关键接口

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/translate` | GET | 返回转写 / 翻译页面 |
| `/auto/uploadAudio` | POST (multipart) | 上传音频 + 目标语言，立刻返回 `{taskId}` |
| `/auto/getResultStream/{taskId}` | GET (text/event-stream) | SSE 通道，事件：`transcription` / `translation` / `end` / `error` |

## 快速开始

### 1. 申请 SiliconFlow API Key

在 [SiliconFlow 控制台](https://cloud.siliconflow.cn) 注册并生成 API Key。

### 2. 配置密钥

推荐用环境变量，避免把密钥写进仓库：

```bash
export SILICON_API_KEY=sk-xxxxxxxxxxxxxxxx
```

也可以直接修改 `src/main/resources/application.yml`：

```yaml
spring:
  ai:
    openai:
      api-key: ${silicon-api-key:YOUR_API_KEY}
```

### 3. 启动

```bash
# 在父模块 springai-alibaba-admin 下
mvn -pl springai-alibaba-voice-translate -am spring-boot:run
```

或者进入本模块单独启动：

```bash
cd springai-alibaba-voice-translate
mvn spring-boot:run
```

### 4. 体验

打开浏览器访问 <http://localhost:8080/translate>，上传一段音频或点击「开始录音」，选择目标语言，等待转写与流式翻译结果。

## 配置项说明

| 配置项 | 默认值 | 用途 |
| --- | --- | --- |
| `spring.ai.openai.api-key` | 环境变量 `silicon-api-key` | SiliconFlow 的 API Key |
| `spring.ai.openai.base-url` | `https://api.siliconflow.cn` | OpenAI 兼容 API 入口 |
| `spring.ai.openai.transcription.options.model` | `FunAudioLLM/SenseVoiceSmall` | 语音转写模型 |
| `spring.ai.openai.chat.options.model` | `Qwen/Qwen2.5-7B-Instruct` | 翻译对话模型 |
| `spring.ai.openai.chat.options.temperature` | `0.2` | 控制译文稳定性 |
| `spring.servlet.multipart.max-file-size` | `25MB` | 单个上传文件大小上限 |

## 常见问题

- **长时间音频转写慢**：当前 SenseVoiceSmall 同步调用，长音频耗时较长。后续可参考文章中的「音频切片 + 并行转写 + 顺序合并」方案做扩展。
- **转写结果为空**：多半是音频格式不支持或音量太低，请确认是 mp3 / wav / m4a 等常见格式。
- **首次启动慢**：需要下载 Spring AI 与依赖的 jar，建议提前在能联网的环境下 `mvn dependency:resolve`。
- **麦克风无法使用**：浏览器要求 HTTPS 才能调用 `getUserMedia`，本地 `localhost` 例外。

## 技术栈

- JDK 17+
- Spring Boot 3.5.9
- Spring AI 1.1.2（OpenAI 兼容客户端）
- Thymeleaf
- 前端原生 `MediaRecorder` + `EventSource`

## 参考

- 原文：[实战干货！Spring AI 集成语音识别，实现实时翻译机器人的完整指南](https://blog.csdn.net/liuyueyi25/article/details/158736440)（CC 4.0 BY-SA）
- Spring AI 文档：<https://docs.spring.io/spring-ai/reference>
- SiliconFlow：<https://cloud.siliconflow.cn>
