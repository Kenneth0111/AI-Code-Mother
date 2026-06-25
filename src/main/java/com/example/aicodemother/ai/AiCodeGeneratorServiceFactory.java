package com.example.aicodemother.ai;

import com.example.aicodemother.ai.tools.ToolManager;
import com.example.aicodemother.exception.BusinessException;
import com.example.aicodemother.exception.ErrorCode;
import com.example.aicodemother.model.enums.CodeGenTypeEnum;
import com.example.aicodemother.service.ChatHistoryService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@Slf4j
public class AiCodeGeneratorServiceFactory {

    public static final int VUE_PROJECT_MAX_SEQUENTIAL_TOOL_INVOCATIONS = 2;

    @Resource
    private ChatModel chatModel;

    @Resource
    private StreamingChatModel openAiStreamingChatModel;

    @Resource
    private StreamingChatModel reasoningStreamingChatModel;

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private ToolManager toolManager;

    /**
     * AI 服务实例缓存
     * 缓存策略：
     * - 最大缓存 1000 个实例
     * - 写入后 30 分钟过期
     * - 访问后 30 分钟过期（与写入对齐，避免用户在预览页停留时缓存被驱逐导致 AI 失忆）
     */
    private final Cache<String, AiCodeGeneratorService> serviceCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(30))
            .removalListener((key, value, cause) -> log.debug("AI 服务实例被移除，appId: {}, 原因: {}", key, cause))
            .build();

    /**
     * 获取 AI 服务实例
     *
     * @param appId 应用 ID
     * @return AI 服务实例
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId) {
        return getAiCodeGeneratorService(appId, CodeGenTypeEnum.HTML);
    }

    /**
     * 根据 appId 获取服务
     *
     * @param appId       应用ID
     * @param codeGenType 代码生成类型
     *                    return
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
        String cacheKey = buildCacheKey(appId, codeGenType);
        return serviceCache.get(cacheKey, key -> createAiCodeGeneratorService(appId, codeGenType));
    }

    /**
     * 构建缓存 key
     *
     * @param appId       应用ID
     * @param codeGenType 码生成类型
     * @return 缓存 key
     */
    private String buildCacheKey(long appId, CodeGenTypeEnum codeGenType) {
        return appId + "_" + codeGenType.getValue();
    }

    /**
     * 创建新的 AI 服务实例
     *
     * @param appId       应用 ID
     * @param codeGenType 代码生成类型
     * @return AI 服务实例
     */
    private AiCodeGeneratorService createAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
        log.info("为 appId: {} 创建新的 AI 服务实例", appId);
        // 根据 appId 构建独立的对话记忆
        // 使用 SafeChatMemoryStore 包裹 Redis 存储：读取记忆时清理残缺的工具调用消息，
        // 避免把非法对话（带 tool_calls 却缺少对应结果）发给模型导致 400 -> SSE 500
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .id(appId)
                .maxMessages(100)
                .chatMemoryStore(new SafeChatMemoryStore(redisChatMemoryStore))
                .build();
        // 所有模式都从数据库加载历史对话，保证 Caffeine 缓存过期或服务重启后仍能重建上下文，
        // 避免 AI 在多轮对话中"失忆"后重新声明意图（重复出现"我将创建..."）。
        // loadChatHistoryToMemory 只构造纯文本 UserMessage / AiMessage，不带 tool_calls，与 SafeChatMemoryStore 兼容。
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);
        return switch (codeGenType) {
            case VUE_PROJECT -> AiServices.builder(AiCodeGeneratorService.class)
                    .streamingChatModel(reasoningStreamingChatModel)
                    .chatMemoryProvider(memoryId -> chatMemory)
                    .tools((Object[]) toolManager.getAllTools())
                    // 硬上限：使用批量写入后，正常生成只需 3-5 次工具调用
                    // 设为 15 次足够应对修改场景，同时防止循环失控
                    .maxSequentialToolsInvocations(VUE_PROJECT_MAX_SEQUENTIAL_TOOL_INVOCATIONS)
                    // 处理工具调用幻觉问题
                    .hallucinatedToolNameStrategy(toolExecutionRequest -> ToolExecutionResultMessage.from(toolExecutionRequest, "Error: there is no tool called " + toolExecutionRequest.name()))
                    .build();
            case HTML, MULTI_FILE -> AiServices.builder(AiCodeGeneratorService.class)
                    .chatModel(chatModel)
                    .streamingChatModel(openAiStreamingChatModel)
                    .chatMemory(chatMemory)
                    .build();
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型" + codeGenType);
        };
    }

    @Bean
    public AiCodeGeneratorService aiCodeGeneratorService() {
        return getAiCodeGeneratorService(0L);
    }
}
