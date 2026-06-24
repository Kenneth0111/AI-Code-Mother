package com.example.aicodemother.ai.metrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 按 appId 维度持有 {@link GenerationMetrics}，供 Facade 在 TokenStream 回调里读写，
 * 以及 {@link ToolMetricsAspect} 在工具调用层叠加分类计数。
 *
 * 生命周期：
 * - start(appId)  ：进入 processTokenStream 时调用
 * - finish(appId) ：onCompleteResponse / onError 时调用，输出汇总日志并清理
 *
 * 线程安全：内部 ConcurrentHashMap + GenerationMetrics 内部均为原子字段。
 */
@Slf4j
@Component
public class GenerationMetricsCollector {

    private final Map<Long, GenerationMetrics> activeMetrics = new ConcurrentHashMap<>();

    public GenerationMetrics start(long appId) {
        GenerationMetrics metrics = new GenerationMetrics(appId);
        activeMetrics.put(appId, metrics);
        log.info("[METRICS] start appId={}", appId);
        return metrics;
    }

    public GenerationMetrics get(long appId) {
        return activeMetrics.get(appId);
    }

    public void recordToolCall(long appId, String toolName, long durationNanos, ToolCallStat.ToolResult result) {
        GenerationMetrics metrics = activeMetrics.get(appId);
        if (metrics == null) {
            return;
        }
        metrics.getToolAccumulatedNanos().addAndGet(durationNanos);
        metrics.incrementTool(toolName);
        switch (result) {
            case MODIFY_NO_MATCH -> metrics.getModifyNoMatchCount().incrementAndGet();
            case FILE_NOT_FOUND -> metrics.getFileNotFoundCount().incrementAndGet();
            case ERROR -> metrics.getToolErrorCount().incrementAndGet();
            default -> {}
        }
    }

    public void finish(long appId, String terminationReason) {
        GenerationMetrics metrics = activeMetrics.remove(appId);
        if (metrics == null) {
            return;
        }
        metrics.setTotalEndNanos(System.nanoTime());
        metrics.setTerminationReason(terminationReason);

        String perTool = metrics.getPerToolCount().entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue().get())
                .collect(Collectors.joining(","));

        long total = metrics.totalDurationMs();
        long llm = metrics.llmDurationMs();
        long tool = metrics.toolDurationMs();
        long other = Math.max(0, total - llm - tool);

        log.info("[METRICS] finish appId={} total_ms={} llm_ms={} tool_ms={} other_ms={} " +
                        "tool_calls={} modify_no_match={} file_not_found={} tool_error={} " +
                        "in_tokens={} out_tokens={} model={} reason={} per_tool=[{}]",
                metrics.getAppId(),
                total,
                llm,
                tool,
                other,
                metrics.getToolCallCount().get(),
                metrics.getModifyNoMatchCount().get(),
                metrics.getFileNotFoundCount().get(),
                metrics.getToolErrorCount().get(),
                metrics.getInputTokens(),
                metrics.getOutputTokens(),
                metrics.getModelName(),
                terminationReason,
                perTool);
    }
}
