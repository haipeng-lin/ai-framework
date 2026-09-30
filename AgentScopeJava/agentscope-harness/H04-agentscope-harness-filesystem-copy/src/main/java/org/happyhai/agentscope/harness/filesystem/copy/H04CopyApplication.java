package org.happyhai.agentscope.harness.filesystem.copy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * H04-copy：H04 的多副本测试入口。跟 H04 一起启动，验证共享存储模式
 * 在多副本下的行为：同一 userId 的会话和记忆在两个进程之间合并。
 */
@SpringBootApplication
public class H04CopyApplication {

    public static void main(String[] args) {
        SpringApplication.run(H04CopyApplication.class, args);
    }
}
