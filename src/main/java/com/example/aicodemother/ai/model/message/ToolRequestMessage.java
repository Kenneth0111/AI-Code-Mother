package com.example.aicodemother.ai.model.message;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.BeforeToolExecution;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 工具调用消息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ToolRequestMessage extends StreamMessage {

    private String id;

    private String name;

    private String arguments;

    public ToolRequestMessage(ToolExecutionRequest toolExecutionRequest) {
        super(StreamMessageTypeEnum.TOOL_REQUEST.getValue());
        this.id = toolExecutionRequest.id();
        this.name = toolExecutionRequest.name();
        this.arguments = toolExecutionRequest.arguments();
    }

    public ToolRequestMessage(BeforeToolExecution beforeToolExecution) {
        super(StreamMessageTypeEnum.TOOL_REQUEST.getValue());
        ToolExecutionRequest request = beforeToolExecution.request();
        this.id = request.id();
        this.name = request.name();
        this.arguments = request.arguments();
    }
}
