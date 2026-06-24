package com.example.aicodemother.ai.metrics;

import lombok.Data;

@Data
public class ToolCallStat {

    private String toolName;

    private long durationMs;

    private ToolResult result;

    private int argSize;

    public enum ToolResult {
        SUCCESS,
        MODIFY_NO_MATCH,
        FILE_NOT_FOUND,
        ERROR
    }
}
