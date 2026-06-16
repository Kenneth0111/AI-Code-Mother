package com.example.aicodemother.ai;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.example.aicodemother.model.enums.CodeGenTypeEnum;
import dev.langchain4j.service.SystemMessage;

/**
 * AI代码生成类型智能路由服务
 * AI 返回原始 JSON 文本，再由本接口解析为枚举，避免 langchain4j 直接解析枚举失败
 */
public interface AiCodeGenTypeRoutingService {

    /**
     * 调用 AI 获取路由结果（原始文本，通常为 JSON）
     *
     * @param userPrompt 用户输入的需求描述
     * @return AI 返回的原始结果
     */
    @SystemMessage(fromResource = "prompt/codegen-routing-system-prompt.txt")
    String routeCodeGenTypeRaw(String userPrompt);

    /**
     * 根据用户需求智能选择代码生成类型
     *
     * @param userPrompt 用户输入的需求描述
     * @return 推荐的代码生成类型
     */
    default CodeGenTypeEnum routeCodeGenType(String userPrompt) {
        String raw = routeCodeGenTypeRaw(userPrompt);
        return parseCodeGenType(raw);
    }

    /**
     * 解析 AI 返回的结果为枚举，兼容 JSON 对象与纯文本两种形式
     *
     * @param raw AI 返回的原始结果
     * @return 对应的代码生成类型枚举
     */
    static CodeGenTypeEnum parseCodeGenType(String raw) {
        if (CharSequenceUtil.isBlank(raw)) {
            return CodeGenTypeEnum.HTML;
        }
        String type = raw.trim();
        // 提取 JSON 中的 type 字段（部分模型可能返回带 ```json 代码块的文本）
        int start = type.indexOf('{');
        int end = type.lastIndexOf('}');
        if (start >= 0 && end > start) {
            JSONObject json = JSONUtil.parseObj(type.substring(start, end + 1));
            type = json.getStr("type", json.getStr("value", ""));
        }
        type = type.trim();
        // 优先按枚举名匹配（如 HTML、MULTI_FILE、VUE_PROJECT）
        for (CodeGenTypeEnum anEnum : CodeGenTypeEnum.values()) {
            if (anEnum.name().equalsIgnoreCase(type)) {
                return anEnum;
            }
        }
        // 兜底：按枚举 value 匹配（如 html、multi_file、vue_project）
        CodeGenTypeEnum byValue = CodeGenTypeEnum.getEnumByValue(type.toLowerCase());
        return byValue != null ? byValue : CodeGenTypeEnum.HTML;
    }

}