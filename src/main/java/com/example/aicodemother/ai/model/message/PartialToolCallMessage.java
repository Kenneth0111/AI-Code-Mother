package com.example.aicodemother.ai.model.message;

import dev.langchain4j.model.chat.response.PartialToolCall;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 工具调用片段消息（流式接收工具调用的部分数据）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class PartialToolCallMessage extends StreamMessage {

    private int index;

    private String id;

    private String name;

    private String partialArguments;

    public PartialToolCallMessage(PartialToolCall partialToolCall) {
        super(StreamMessageTypeEnum.PARTIAL_TOOL_CALL.getValue());
        this.index = partialToolCall.index();
        this.id = partialToolCall.id();
        this.name = partialToolCall.name();
        this.partialArguments = partialToolCall.partialArguments();
    }
}
