package com.example.aicodemother.ai;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 对话记忆存储装饰器：在读取记忆时清理「残缺的工具调用消息」。
 * <p>
 * OpenAI / DeepSeek 等接口要求：带 tool_calls 的 assistant 消息后面必须紧跟覆盖所有
 * tool_call_id 的 tool 结果消息，否则会返回 400（invalid_request_error）。
 * <p>
 * Vue 工程模式带工具调用且仅依赖 Redis 记忆，一旦某次生成被中断 / 报错，Redis 里会残留
 * 「有 tool_calls 却没有对应结果」的消息，导致之后每次追加对话都报错（接口表现为 500）。
 * 这里在读取时做一次清理，保证发送给模型的对话始终合法。
 */
@Slf4j
public class SafeChatMemoryStore implements ChatMemoryStore {

    private final ChatMemoryStore delegate;

    public SafeChatMemoryStore(ChatMemoryStore delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        return sanitize(delegate.getMessages(memoryId));
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        delegate.updateMessages(memoryId, messages);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        delegate.deleteMessages(memoryId);
    }

    /**
     * 清理消息列表，保证工具调用消息的完整性：
     * <ol>
     *     <li>带 toolExecutionRequests 的 AiMessage，必须紧跟覆盖其所有 id 的工具结果消息，否则整组丢弃；</li>
     *     <li>没有前置工具调用的孤立工具结果消息，直接丢弃。</li>
     * </ol>
     */
    static List<ChatMessage> sanitize(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return messages;
        }
        List<ChatMessage> result = new ArrayList<>(messages.size());
        int removed = 0;
        int i = 0;
        while (i < messages.size()) {
            ChatMessage msg = messages.get(i);
            if (msg instanceof AiMessage aiMessage && aiMessage.hasToolExecutionRequests()) {
                // 收集紧随其后的工具结果消息
                List<ToolExecutionResultMessage> results = new ArrayList<>();
                int j = i + 1;
                while (j < messages.size() && messages.get(j) instanceof ToolExecutionResultMessage trm) {
                    results.add(trm);
                    j++;
                }
                Set<String> requestIds = new HashSet<>();
                for (ToolExecutionRequest req : aiMessage.toolExecutionRequests()) {
                    requestIds.add(req.id());
                }
                Set<String> resultIds = new HashSet<>();
                for (ToolExecutionResultMessage trm : results) {
                    resultIds.add(trm.id());
                }
                if (resultIds.containsAll(requestIds)) {
                    // 完整，保留这一组消息
                    result.add(aiMessage);
                    result.addAll(results);
                } else {
                    // 残缺，丢弃 AiMessage 及其部分结果，避免请求非法
                    removed += 1 + results.size();
                }
                i = j;
            } else if (msg instanceof ToolExecutionResultMessage) {
                // 没有前置工具调用的孤立结果消息，丢弃
                removed++;
                i++;
            } else {
                result.add(msg);
                i++;
            }
        }
        if (removed > 0) {
            log.warn("已清理 {} 条残缺的工具调用记忆消息，避免大模型请求非法", removed);
        }
        return result;
    }
}
