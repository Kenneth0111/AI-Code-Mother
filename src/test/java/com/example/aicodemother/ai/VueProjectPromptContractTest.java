package com.example.aicodemother.ai;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

class VueProjectPromptContractTest {

    @Test
    void vueProjectPromptKeepsInfrastructureOutOfToolPayloads() throws Exception {
        String prompt = readPrompt();

        Assertions.assertTrue(prompt.contains("BACKEND_SCAFFOLD_READY"));
        Assertions.assertTrue(prompt.contains("DO_NOT_WRITE_INFRASTRUCTURE_FILES"));
        Assertions.assertTrue(prompt.contains("MAX_BATCH_WRITE_CALLS=2"));
        Assertions.assertTrue(prompt.contains("MUST_CALL_EXIT_AFTER_BATCH_WRITES"));
        Assertions.assertTrue(prompt.contains("package.json"));
        Assertions.assertTrue(prompt.contains("vite.config.js"));
        Assertions.assertTrue(prompt.contains("index.html"));
        Assertions.assertTrue(prompt.contains("src/main.js"));
        Assertions.assertTrue(prompt.contains("src/App.vue"));
        Assertions.assertTrue(prompt.contains("Do not write package.json"));
        Assertions.assertTrue(prompt.contains("Do not write vite.config.js"));
        Assertions.assertTrue(prompt.contains("Do not write index.html"));
        Assertions.assertTrue(prompt.contains("Do not write src/main.js"));
        Assertions.assertTrue(prompt.contains("Do not write src/App.vue"));
    }

    private String readPrompt() throws Exception {
        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("prompt/codegen-vue-project-system-prompt.txt")) {
            Assertions.assertNotNull(inputStream);
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
