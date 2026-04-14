package com.example.aicodemother;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.aicodemother.mapper")
public class AiCodeMotherBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiCodeMotherBackendApplication.class, args);
    }

}
