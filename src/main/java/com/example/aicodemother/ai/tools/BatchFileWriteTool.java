package com.example.aicodemother.ai.tools;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.hutool.core.io.FileUtil;
import com.example.aicodemother.constant.AppConstant;
import com.example.aicodemother.core.builder.VueProjectScaffold;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import jakarta.annotation.Resource;
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

@Slf4j
@Component
public class BatchFileWriteTool extends BaseTool {

    @Resource
    private VueProjectScaffold vueProjectScaffold;

    @Tool("Batch write multiple files. The files parameter is a JSON array string, each item containing path and content.")
    public String batchWriteFiles(@P("JSON array string, each item contains path and content") String files,
                                  @ToolMemoryId Long appId) {
        List<JSONObject> fileList;
        try {
            fileList = JSONUtil.toList(JSONUtil.parseArray(files), JSONObject.class);
        } catch (Exception e) {
            return "Parameter parse failed. files must be a valid JSON array: " + e.getMessage();
        }
        if (fileList.isEmpty()) {
            return "[BATCH_WRITE] files=0 success=0 failed=0 total_bytes=0";
        }

        Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, "vue_project_" + appId);
        if (vueProjectScaffold != null) {
            vueProjectScaffold.ensureScaffold(projectRoot);
        }

        List<String> successFiles = new ArrayList<>();
        List<String> failedFiles = new ArrayList<>();
        long totalBytes = 0L;

        for (JSONObject fileObj : fileList) {
            String relativeFilePath = fileObj.getStr("path");
            String content = fileObj.getStr("content");
            if (relativeFilePath == null || content == null) {
                failedFiles.add(relativeFilePath + " (missing path or content)");
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
                byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
                Files.write(path, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                successFiles.add(relativeFilePath);
                totalBytes += bytes.length;
            } catch (IOException e) {
                failedFiles.add(relativeFilePath + " (" + e.getMessage() + ")");
                log.error("Batch file write failed: {}", relativeFilePath, e);
            }
        }

        log.info("[BATCH_WRITE] appId={} files={} success={} failed={} total_bytes={} projectRoot={}",
                appId, fileList.size(), successFiles.size(), failedFiles.size(), totalBytes, projectRoot);

        StringBuilder result = new StringBuilder();
        result.append(String.format("[BATCH_WRITE] files=%d success=%d failed=%d total_bytes=%d",
                fileList.size(), successFiles.size(), failedFiles.size(), totalBytes));
        if (!failedFiles.isEmpty()) {
            result.append("\nfailed_files=").append(String.join(", ", failedFiles));
        }
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

    private String buildBatchWriteSummary(JSONArray fileArray) {
        long totalBytes = 0L;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[tool] batchWriteFiles completed: files=%d%n", fileArray.size()));
        for (int i = 0; i < fileArray.size(); i++) {
            JSONObject fileObj = fileArray.getJSONObject(i);
            String path = fileObj.getStr("path");
            String content = fileObj.getStr("content");
            int bytes = content == null ? 0 : content.getBytes(StandardCharsets.UTF_8).length;
            totalBytes += bytes;
            if (i < 50) {
                sb.append(String.format("- %s (%d bytes)%n", path, bytes));
                if (content != null) {
                    String lang = inferLang(path);
                    sb.append("```").append(lang).append("\n");
                    sb.append(content);
                    if (!content.endsWith("\n")) {
                        sb.append("\n");
                    }
                    sb.append("```\n");
                }
            }
        }
        if (fileArray.size() > 50) {
            sb.append(String.format("... %d more files omitted%n", fileArray.size() - 50));
        }
        sb.append(String.format("total_bytes=%d%n", totalBytes));
        return sb.toString();
    }

    private String inferLang(String filePath) {
        String suffix = FileUtil.getSuffix(filePath);
        if (suffix == null || suffix.isBlank()) {
            return "plaintext";
        }
        return switch (suffix.toLowerCase()) {
            case "js", "mjs", "cjs" -> "javascript";
            case "ts" -> "typescript";
            case "vue" -> "vue";
            case "json" -> "json";
            case "css" -> "css";
            case "scss" -> "scss";
            case "less" -> "less";
            case "html" -> "html";
            case "md" -> "markdown";
            case "yml", "yaml" -> "yaml";
            case "svg" -> "xml";
            default -> suffix.toLowerCase();
        };
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String filesStr = arguments.getStr("files");
        if (filesStr == null) {
            return "\n\n[tool] batchWriteFiles: no files data\n\n";
        }
        try {
            JSONArray fileArray = JSONUtil.parseArray(filesStr);
            return buildBatchWriteSummary(fileArray);
        } catch (Exception e) {
            return "\n\n[tool] batchWriteFiles: failed to parse arguments\n\n";
        }
    }
}
