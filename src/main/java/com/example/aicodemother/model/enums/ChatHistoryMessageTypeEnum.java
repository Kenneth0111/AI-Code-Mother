package com.example.aicodemother.model.enums;

import cn.hutool.core.util.ObjectUtil;
import lombok.Getter;

/**
 * 对话消息类型枚举
 */
@Getter
public enum ChatHistoryMessageTypeEnum {

    USER("用户消息", "user"),
    AI("AI 消息", "ai");

    private final String text;
    private final String value;

    ChatHistoryMessageTypeEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value 枚举值的 value
     * @return 枚举值
     */
    public static ChatHistoryMessageTypeEnum getEnumByValue(String value) {
        if (ObjectUtil.isEmpty(value)) {
            return null;
        }
        for (ChatHistoryMessageTypeEnum anEnum : ChatHistoryMessageTypeEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }
}
