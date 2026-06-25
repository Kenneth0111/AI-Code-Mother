package com.example.aicodemother.ai.metrics;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class GenerationMetricsTest {

    @Test
    void recordCallbackEventTracksGapsAndTerminalGap() throws Exception {
        GenerationMetrics metrics = new GenerationMetrics(1L);

        metrics.recordCallbackEvent(false);
        Thread.sleep(2);
        metrics.recordCallbackEvent(false);
        Thread.sleep(2);
        metrics.recordCallbackEvent(true);

        Assertions.assertTrue(metrics.callbackGapDurationMs() > 0);
        Assertions.assertTrue(metrics.maxCallbackGapDurationMs() > 0);
        Assertions.assertTrue(metrics.terminalCallbackGapDurationMs() > 0);
    }
}
