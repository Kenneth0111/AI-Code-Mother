package com.example.aicodemother.ai.metrics;

import lombok.Data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Vue 项目生成的耗时埋点上下文，按 appId 维度存储。
 * 字段语义：
 * - totalStartNanos / totalEndNanos：用户请求进入 processTokenStream → 流式 onCompleteResponse 的全过程
 * - llmAccumulatedNanos：onPartialResponse 持续期间累计的时间（近似 LLM 真正在吐 token 的时间）
 * - toolAccumulatedNanos：beforeToolExecution → onToolExecuted 之间的累计时间
 * - toolCallCount：实际触发 onToolExecuted 的次数
 * - perToolCount：按工具名分布
 * - modifyNoMatchCount：modifyFile 返回"未找到要替换的内容"的次数（来自 ToolMetricsAspect）
 * - inputTokens / outputTokens：来自最终 ChatResponse.tokenUsage()
 */
@Data
public class GenerationMetrics {

    private final long appId;

    private final long totalStartNanos = System.nanoTime();

    private long totalEndNanos;

    private final AtomicLong firstEventNanos = new AtomicLong();

    private final AtomicLong firstAiResponseNanos = new AtomicLong();

    private final AtomicLong firstToolRequestNanos = new AtomicLong();

    private final AtomicLong firstToolExecutedNanos = new AtomicLong();

    private final AtomicLong llmAccumulatedNanos = new AtomicLong();

    private final AtomicLong toolAccumulatedNanos = new AtomicLong();

    private final AtomicLong toolWaitAccumulatedNanos = new AtomicLong();

    private final AtomicLong sinkNextAccumulatedNanos = new AtomicLong();

    private final AtomicLong maxSinkNextNanos = new AtomicLong();

    private final AtomicInteger toolCallCount = new AtomicInteger();

    private final AtomicInteger aiResponseChunkCount = new AtomicInteger();

    private final AtomicInteger partialToolCallChunkCount = new AtomicInteger();

    private final AtomicInteger toolRequestEventCount = new AtomicInteger();

    private final AtomicInteger toolExecutedEventCount = new AtomicInteger();

    private final Map<String, AtomicInteger> perToolCount = new ConcurrentHashMap<>();

    private final AtomicInteger modifyNoMatchCount = new AtomicInteger();

    private final AtomicInteger fileNotFoundCount = new AtomicInteger();

    private final AtomicInteger toolErrorCount = new AtomicInteger();

    private long inputTokens;

    private long outputTokens;

    private String modelName;

    private String terminationReason = "completed";

    public GenerationMetrics(long appId) {
        this.appId = appId;
    }

    public void incrementTool(String toolName) {
        perToolCount.computeIfAbsent(toolName, k -> new AtomicInteger()).incrementAndGet();
        toolCallCount.incrementAndGet();
    }

    public long totalDurationMs() {
        long end = totalEndNanos == 0 ? System.nanoTime() : totalEndNanos;
        return (end - totalStartNanos) / 1_000_000;
    }

    public long llmDurationMs() {
        return llmAccumulatedNanos.get() / 1_000_000;
    }

    public long toolDurationMs() {
        return toolAccumulatedNanos.get() / 1_000_000;
    }

    public long sinceStartMs(long nanos) {
        if (nanos <= 0) {
            return -1;
        }
        return (nanos - totalStartNanos) / 1_000_000;
    }

    public long toolWaitDurationMs() {
        return toolWaitAccumulatedNanos.get() / 1_000_000;
    }

    public long sinkNextDurationMs() {
        return sinkNextAccumulatedNanos.get() / 1_000_000;
    }

    public long maxSinkNextDurationMs() {
        return maxSinkNextNanos.get() / 1_000_000;
    }

    public void recordSinkNext(long durationNanos) {
        sinkNextAccumulatedNanos.addAndGet(durationNanos);
        maxSinkNextNanos.accumulateAndGet(durationNanos, Math::max);
    }
}
