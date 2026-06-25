package com.example.aicodemother.core;

import cn.hutool.json.JSONUtil;
import com.example.aicodemother.ai.AiCodeGeneratorService;
import com.example.aicodemother.ai.AiCodeGeneratorServiceFactory;
import com.example.aicodemother.ai.metrics.GenerationMetrics;
import com.example.aicodemother.ai.metrics.GenerationMetricsCollector;
import com.example.aicodemother.ai.metrics.ToolCallStat;
import com.example.aicodemother.ai.model.HtmlCodeResult;
import com.example.aicodemother.ai.model.MultiFileCodeResult;
import com.example.aicodemother.ai.model.message.AiResponseMessage;
import com.example.aicodemother.ai.model.message.PartialToolCallMessage;
import com.example.aicodemother.ai.model.message.ToolExecutedMessage;
import com.example.aicodemother.ai.model.message.ToolRequestMessage;
import com.example.aicodemother.core.parser.CodeParserExecutor;
import com.example.aicodemother.core.saver.CodeFileSaverExecutor;
import com.example.aicodemother.exception.BusinessException;
import com.example.aicodemother.exception.ErrorCode;
import com.example.aicodemother.model.enums.CodeGenTypeEnum;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.File;

/**
 * AI 代码生成门面类，组合生成和保存功能
 */
@Service
@Slf4j
public class AiCodeGeneratorFacade {

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private GenerationMetricsCollector metricsCollector;

    /**
     * 统一入口：根据类型生成并保存代码
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @param appId           应用ID
     * @return 保存的目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, codeGenTypeEnum, appId);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, codeGenTypeEnum, appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 统一入口：根据类型生成并保存代码（流式）
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @param appId           应用ID
     * @return 保存的目录
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                Flux<String> result = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
                yield processCodeStream(result, codeGenTypeEnum, appId);
            }
            case MULTI_FILE -> {
                Flux<String> result = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
                yield processCodeStream(result, codeGenTypeEnum, appId);
            }
            case VUE_PROJECT -> {
                TokenStream tokenStream = aiCodeGeneratorService.generateVueProjectCodeStream(appId, userMessage);
                yield processTokenStream(tokenStream, appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * （流式）处理代码，并保存代码
     *
     * @param codeStream      代码流
     * @param codeGenTypeEnum 生成类型
     * @param appId           应用ID
     * @return 流式响应
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        StringBuilder codeBuilder = new StringBuilder();
        return codeStream
                .doOnNext(codeBuilder::append)
                .doOnComplete(() -> {
                    try {
                        String completedCode = codeBuilder.toString();
                        Object parsedResult = CodeParserExecutor.executeParser(completedCode, codeGenTypeEnum);
                        File savedDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenTypeEnum, appId);
                        log.info("保存成功，路径为：{}", savedDir.getAbsolutePath());
                    } catch (Exception e) {
                        log.error("保存失败: {}", e.getMessage());
                    }
                });
    }

    /**
     * 将 TokenStream 转换为 Flux<String>，并传递工具调用信息
     *
     * @param tokenStream TokenStream 对象
     * @param appId       应用ID（用于耗时埋点）
     * @return Flux<String> 流式响应
     */
    private Flux<String> processTokenStream(TokenStream tokenStream, Long appId) {
        // LLM 流的当前段起点：onPartialResponse 第一次到达时记录，beforeToolExecution 时累计
        return Flux.create(sink -> {
            GenerationMetrics metrics = metricsCollector.start(appId);
            long[] llmSegmentStartNanos = {System.nanoTime()};
            boolean[] inLlmSegment = {true};
            long[] pendingToolRequestStartNanos = {0L};
            tokenStream.onPartialResponse((String partialResponse) -> {
                    recordCallbackEvent(metrics, false);
                    metrics.getAiResponseChunkCount().incrementAndGet();
                    metrics.getFirstAiResponseNanos().compareAndSet(0, System.nanoTime());
                    if (!inLlmSegment[0]) {
                        // 工具执行后回到 LLM 阶段，重新计时
                        llmSegmentStartNanos[0] = System.nanoTime();
                        inLlmSegment[0] = true;
                    }
                    AiResponseMessage aiResponseMessage = new AiResponseMessage(partialResponse);
                    emitMeasured(sink, metrics, JSONUtil.toJsonStr(aiResponseMessage));
                }).onPartialToolCall(partialToolCall -> {
                    recordCallbackEvent(metrics, false);
                    metrics.getPartialToolCallChunkCount().incrementAndGet();
                    if (!inLlmSegment[0]) {
                        llmSegmentStartNanos[0] = System.nanoTime();
                        inLlmSegment[0] = true;
                    }
                    PartialToolCallMessage partialToolCallMessage = new PartialToolCallMessage(partialToolCall);
                    emitMeasured(sink, metrics, JSONUtil.toJsonStr(partialToolCallMessage));
                }).beforeToolExecution(beforeToolExecution -> {
                    recordCallbackEvent(metrics, false);
                    metrics.getToolRequestEventCount().incrementAndGet();
                    metrics.getFirstToolRequestNanos().compareAndSet(0, System.nanoTime());
                    if (inLlmSegment[0]) {
                        metrics.getLlmAccumulatedNanos().addAndGet(System.nanoTime() - llmSegmentStartNanos[0]);
                        inLlmSegment[0] = false;
                    }
                    pendingToolRequestStartNanos[0] = System.nanoTime();
                    ToolRequestMessage toolRequestMessage = new ToolRequestMessage(beforeToolExecution);
                    emitMeasured(sink, metrics, JSONUtil.toJsonStr(toolRequestMessage));
                }).onToolExecuted((ToolExecution toolExecution) -> {
                    recordCallbackEvent(metrics, false);
                    metrics.getToolExecutedEventCount().incrementAndGet();
                    metrics.getFirstToolExecutedNanos().compareAndSet(0, System.nanoTime());
                    if (pendingToolRequestStartNanos[0] > 0) {
                        metrics.getToolWaitAccumulatedNanos()
                                .addAndGet(System.nanoTime() - pendingToolRequestStartNanos[0]);
                        pendingToolRequestStartNanos[0] = 0L;
                    }
                    recordToolExecution(appId, toolExecution);
                    ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
                    emitMeasured(sink, metrics, JSONUtil.toJsonStr(toolExecutedMessage));
                })
                .onCompleteResponse((ChatResponse response) -> {
                    recordCallbackEvent(metrics, true);
                    if (inLlmSegment[0]) {
                        metrics.getLlmAccumulatedNanos().addAndGet(System.nanoTime() - llmSegmentStartNanos[0]);
                        inLlmSegment[0] = false;
                    }
                    if (response != null && response.tokenUsage() != null) {
                        Integer in = response.tokenUsage().inputTokenCount();
                        Integer out = response.tokenUsage().outputTokenCount();
                        if (in != null) metrics.setInputTokens(in);
                        if (out != null) metrics.setOutputTokens(out);
                    }
                    if (response != null && response.modelName() != null) {
                        metrics.setModelName(response.modelName());
                    }
                    metricsCollector.finish(appId, "completed");
                    sink.complete();
                })
                .onError((Throwable error) -> {
                    recordCallbackEvent(metrics, true);
                    if (inLlmSegment[0]) {
                        metrics.getLlmAccumulatedNanos().addAndGet(System.nanoTime() - llmSegmentStartNanos[0]);
                        inLlmSegment[0] = false;
                    }
                    String errorMsg = error.getMessage() != null ? error.getMessage() : "";
                    if (errorMsg.contains("exceeded") && errorMsg.contains("sequential tool invocations")) {
                        log.warn("工具调用次数超限，优雅结束流: {}", errorMsg);
                        AiResponseMessage msg = new AiResponseMessage("\n\n[系统提示] 工具调用次数已达上限，操作已结束。已生成的文件请检查预览确认效果。");
                        emitMeasured(sink, metrics, JSONUtil.toJsonStr(msg));
                        metricsCollector.finish(appId, "tool_limit_exceeded");
                        sink.complete();
                    } else if (isConnectionError(error)) {
                        log.warn("网络连接异常，优雅结束流: {}", errorMsg);
                        AiResponseMessage msg = new AiResponseMessage("\n\n[系统提示] 网络连接中断，已生成的文件已保存。如需继续生成，请重新发送消息。");
                        emitMeasured(sink, metrics, JSONUtil.toJsonStr(msg));
                        metricsCollector.finish(appId, "connection_error");
                        sink.complete();
                    } else {
                        log.error("TokenStream 处理失败", error);
                        AiResponseMessage msg = new AiResponseMessage("\n\n[系统提示] 生成过程出现异常：" + errorMsg + "。请稍后重试。");
                        emitMeasured(sink, metrics, JSONUtil.toJsonStr(msg));
                        metricsCollector.finish(appId, "error");
                        sink.complete();
                    }
                })
                .start();
        });
    }

    private void recordCallbackEvent(GenerationMetrics metrics, boolean terminal) {
        metrics.recordCallbackEvent(terminal);
    }

    private void emitMeasured(FluxSink<String> sink, GenerationMetrics metrics, String payload) {
        long start = System.nanoTime();
        sink.next(payload);
        metrics.recordSinkNext(System.nanoTime() - start);
    }

    private void recordToolExecution(Long appId, ToolExecution toolExecution) {
        if (appId == null || toolExecution == null || toolExecution.request() == null) {
            return;
        }
        String toolName = toolExecution.request().name();
        long durationNanos = toolExecution.duration() != null ? toolExecution.duration().toNanos() : 0L;
        ToolCallStat.ToolResult result = classifyToolResult(toolExecution);
        metricsCollector.recordToolCall(appId, toolName, durationNanos, result);
        log.info("[TOOL_METRICS] appId={} tool={} dur_ms={} result={}",
                appId, toolName, durationNanos / 1_000_000, result);
    }

    private ToolCallStat.ToolResult classifyToolResult(ToolExecution toolExecution) {
        if (toolExecution.hasFailed()) {
            return ToolCallStat.ToolResult.ERROR;
        }
        String s = toolExecution.result();
        if (s == null) {
            return ToolCallStat.ToolResult.SUCCESS;
        }
        if (s.startsWith("警告：文件中未找到要替换的内容")) {
            return ToolCallStat.ToolResult.MODIFY_NO_MATCH;
        }
        if (s.startsWith("错误：文件不存在") || s.startsWith("警告：文件不存在")) {
            return ToolCallStat.ToolResult.FILE_NOT_FOUND;
        }
        if (s.startsWith("错误") || s.contains("失败:")) {
            return ToolCallStat.ToolResult.ERROR;
        }
        return ToolCallStat.ToolResult.SUCCESS;
    }

    private boolean isConnectionError(Throwable error) {
        Throwable cause = error;
        while (cause != null) {
            String msg = cause.getMessage();
            if (msg != null && (msg.contains("Connection reset")
                    || msg.contains("Connection refused")
                    || msg.contains("connect timed out")
                    || msg.contains("Read timed out")
                    || msg.contains("Broken pipe")
                    || msg.contains("Connection closed")
                    || msg.contains("Stream closed")
                    || msg.contains("SocketException")
                    || msg.contains("EOFException"))) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

}
