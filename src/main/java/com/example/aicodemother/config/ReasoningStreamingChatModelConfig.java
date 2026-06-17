package com.example.aicodemother.config;

import dev.langchain4j.http.client.spring.restclient.SpringRestClientBuilder;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.chat-model")
@Data
public class ReasoningStreamingChatModelConfig {

    private String baseUrl;

    private String apiKey;

    @Bean
    public StreamingChatModel reasoningStreamingChatModel() {
        final String modelName = "qwen-plus";
        final int maxTokens = 32768;

        SpringRestClientBuilder httpClientBuilder =
                new SpringRestClientBuilder()
                        .connectTimeout(Duration.ofSeconds(60))
                        .readTimeout(Duration.ofMinutes(10))
                        .createDefaultStreamingRequestExecutor(true);

        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .maxTokens(maxTokens)
                .timeout(Duration.ofMinutes(10))
                .httpClientBuilder(httpClientBuilder)
                .customHeaders(Map.of(
                        "Connection", "keep-alive"))
                .logRequests(true)
                .logResponses(true)
                .build();
    }
}
