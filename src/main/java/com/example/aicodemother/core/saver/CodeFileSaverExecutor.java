package com.example.aicodemother.core.saver;

import com.example.aicodemother.ai.model.HtmlCodeResult;
import com.example.aicodemother.ai.model.MultiFileCodeResult;
import com.example.aicodemother.exception.BusinessException;
import com.example.aicodemother.exception.ErrorCode;
import com.example.aicodemother.model.enums.CodeGenTypeEnum;

import java.io.File;

/**
 * 代码文件保存执行器
 * 根据不同的代码生成类型选择对应的保存器
 */
public class CodeFileSaverExecutor {

    private CodeFileSaverExecutor() {
    }

    private static final HtmlCodeFileSaverTemplate htmlCodeFileSaver = new HtmlCodeFileSaverTemplate();

    private static final MultiFileCodeFileSaverTemplate multiFileCodeFileSaver = new MultiFileCodeFileSaverTemplate();

    /**
     * 执行保存逻辑
     *
     * @param result          代码结果对象（HtmlCodeResult 或 MultiFileCodeResult）
     * @param codeGenTypeEnum 代码生成类型
     * @return 保存的目录
     */
    public static File executeSaver(Object result, CodeGenTypeEnum codeGenTypeEnum) {
        return switch (codeGenTypeEnum) {
            case HTML -> htmlCodeFileSaver.saveCode((HtmlCodeResult) result);
            case MULTI_FILE -> multiFileCodeFileSaver.saveCode((MultiFileCodeResult) result);
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型");
        };
    }
}
