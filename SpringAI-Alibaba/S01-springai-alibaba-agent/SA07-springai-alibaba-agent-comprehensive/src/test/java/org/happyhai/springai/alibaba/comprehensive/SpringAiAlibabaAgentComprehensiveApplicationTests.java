package org.happyhai.springai.alibaba.comprehensive;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379",
    "spring.data.redis.password=123456"
})
class SpringAiAlibabaAgentComprehensiveApplicationTests {

    @Test
    void contextLoads() {
    }
}
