package com.example.aicodemother.ai;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AiCodeGeneratorServiceFactoryContractTest {

    @Test
    void vueProjectToolInvocationLimitMatchesPromptBudget() {
        Assertions.assertEquals(3, AiCodeGeneratorServiceFactory.VUE_PROJECT_MAX_SEQUENTIAL_TOOL_INVOCATIONS);
    }
}
