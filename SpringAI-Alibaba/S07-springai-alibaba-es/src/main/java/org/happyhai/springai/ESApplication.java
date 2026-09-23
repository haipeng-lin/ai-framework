package org.happyhai.springai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ESApplication {

    public static void main(String[] args) {
        System.out.println("key:" + System.getenv("DASHSCOPE_API_KEY"));
        SpringApplication.run(ESApplication.class, args);
    }

}
