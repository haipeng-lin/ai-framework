package org.happyhai.agentscope.harness.filesystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * H04：共享存储模式（RemoteFilesystemSpec + Redis）演示工程的启动入口。
 */
@SpringBootApplication
public class H04AgentscopeHarnessFilesystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(H04AgentscopeHarnessFilesystemApplication.class, args);
    }
}
