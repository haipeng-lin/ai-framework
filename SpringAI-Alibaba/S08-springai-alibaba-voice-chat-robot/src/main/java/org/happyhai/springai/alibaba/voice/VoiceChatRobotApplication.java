package org.happyhai.springai.alibaba.voice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.client.RestClient;

/**
 * 实时语音聊天机器人入口。
 *
 * <p>关键：显式声明 {@link RestClient.Builder} Bean，强制使用 JDK HttpClient
 * 并注册表单 / 资源 / 字符串消息转换器。Spring AI 的 OpenAI 音频转录底层会
 * 通过 {@code RestClient} 发送 multipart/form-data，而默认配置下的
 * {@code RestClient} 使用的 Client 不支持 multipart，会导致音频上传时被解析
 * 成空体，上游返回 400。参考 T05-voice-chat-robot 项目的写法。</p>
 *
 * 启动诊断日志会在应用就绪后打印实际加载到的 token 及其来源 PropertySource，方便排查 401 / placeholder 解析失败的问题。</p>
 */
@SpringBootApplication
public class VoiceChatRobotApplication {

    public static void main(String[] args) {
        SpringApplication.run(VoiceChatRobotApplication.class, args);
        System.out.println("启动成功，前端测试访问地址： http://localhost:10021/translate");
    }
}

