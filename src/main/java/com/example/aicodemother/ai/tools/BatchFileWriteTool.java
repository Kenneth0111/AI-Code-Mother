package com.example.aicodemother.ai.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.example.aicodemother.constant.AppConstant;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 批量文件写入工具
 * 支持 AI 通过一次工具调用写入多个文件，大幅减少工具调用轮次
 */
@Slf4j
@Component
public class BatchFileWriteTool extends BaseTool {

    @Tool("批量写入多个文件。参数 files 是 JSON 数组字符串，每个元素包含 path（相对路径）和 content（文件内容）。示例：[{\"path\":\"src/main.js\",\"content\":\"...\"}]")
    public String batchWriteFiles(@P("JSON 数组字符串，每个元素包含 path 和 content 字段") String files,
                                  @ToolMemoryId Long appId) {
        List<JSONObject> fileList;
        try {
            fileList = JSONUtil.toList(JSONUtil.parseArray(files), JSONObject.class);
        } catch (Exception e) {
            return "参数解析失败，请确保 files 是合法的 JSON 数组: " + e.getMessage();
        }
        if (fileList.isEmpty()) {
            return "文件列表为空，未执行任何操作";
        }

        String projectDirName = "vue_project_" + appId;
        Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName);
        List<String> successFiles = new ArrayList<>();
        List<String> failedFiles = new ArrayList<>();

        for (JSONObject fileObj : fileList) {
            String relativeFilePath = fileObj.getStr("path");
            String content = fileObj.getStr("content");
            if (relativeFilePath == null || content == null) {
                failedFiles.add(relativeFilePath + " (缺少 path 或 content)");
                continue;
            }
            try {
                Path path = Paths.get(relativeFilePath);
                if (!path.isAbsolute()) {
                    path = projectRoot.resolve(relativeFilePath);
                }
                Path parentDir = path.getParent();
                if (parentDir != null) {
                    Files.createDirectories(parentDir);
                }
                Files.write(path, content.getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
                successFiles.add(relativeFilePath);
            } catch (IOException e) {
                failedFiles.add(relativeFilePath + " (" + e.getMessage() + ")");
                log.error("批量写入失败: {}", relativeFilePath, e);
            }
        }

        StringBuilder result = new StringBuilder();
        result.append(String.format("批量写入完成: 成功 %d 个, 失败 %d 个", successFiles.size(), failedFiles.size()));
        if (!failedFiles.isEmpty()) {
            result.append("\n失败文件: ").append(String.join(", ", failedFiles));
        }
        log.info("批量写入 {} 个文件到项目: {}", successFiles.size(), projectRoot);
        return result.toString();
    }

    @Override
    public String getToolName() {
        return "batchWriteFiles";
    }

    @Override
    public String getDisplayName() {
        return "批量写入文件";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String filesStr = arguments.getStr("files");
        if (filesStr == null) {
            return "\n\n[工具调用] 批量写入文件（无数据）\n\n";
        }
        try {
            JSONArray fileArray = JSONUtil.parseArray(filesStr);
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("[工具调用] 批量写入 %d 个文件\n", fileArray.size()));
            for (int i = 0; i < fileArray.size(); i++) {
                JSONObject fileObj = fileArray.getJSONObject(i);
                String path = fileObj.getStr("path");
                String content = fileObj.getStr("content");
                String suffix = FileUtil.getSuffix(path);
                sb.append(String.format("\n--- %s ---\n```%s\n%s\n```\n", path, suffix, content));
            }
            return sb.toString();
        } catch (Exception e) {
            return "\n\n[工具调用] 批量写入文件（解析失败）\n\n";
        }
    }
}
