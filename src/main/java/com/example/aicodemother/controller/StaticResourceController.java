package com.example.aicodemother.controller;

import com.example.aicodemother.common.BaseResponse;
import com.example.aicodemother.common.ResultUtils;
import com.example.aicodemother.constant.AppConstant;
import com.example.aicodemother.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 静态资源访问
 */
@RestController
@RequestMapping("/static")
public class StaticResourceController {

    // 应用生成根目录（用于浏览）
    private static final String PREVIEW_ROOT_DIR = AppConstant.CODE_OUTPUT_ROOT_DIR;
    private static final Set<String> EXCLUDED_SOURCE_DIRS = Set.of("dist", "node_modules");
    private static final Set<String> TEXT_FILE_EXTENSIONS = Set.of(
            "html", "css", "js", "mjs", "cjs", "ts", "vue", "json", "md",
            "txt", "yml", "yaml", "scss", "less", "svg", "gitignore", "env"
    );
    private static final Set<String> TEXT_FILE_NAMES = Set.of(
            "package.json", "package-lock.json", "vite.config.js", "vite.config.ts",
            "index.html", "README.md", ".env", ".env.development", ".gitignore"
    );

    public record SourceFileVO(String path, String name, String content, String lang) {
    }

    @GetMapping("/{deployKey}/source-files")
    public BaseResponse<List<SourceFileVO>> listSourceFiles(@PathVariable String deployKey) {
        try {
            Path previewRoot = Path.of(PREVIEW_ROOT_DIR).toAbsolutePath().normalize();
            Path projectRoot = previewRoot.resolve(deployKey).normalize();
            if (!projectRoot.startsWith(previewRoot) || !Files.isDirectory(projectRoot)) {
                return ResultUtils.success(List.of());
            }
            try (Stream<Path> paths = Files.walk(projectRoot)) {
                List<SourceFileVO> files = paths
                        .filter(Files::isRegularFile)
                        .filter(path -> shouldIncludeSourceFile(projectRoot, path))
                        .sorted(Comparator.comparing(path -> toRelativePath(projectRoot, path)))
                        .map(path -> toSourceFile(projectRoot, path))
                        .toList();
                return ResultUtils.success(files);
            }
        } catch (Exception e) {
            return new BaseResponse<>(ErrorCode.SYSTEM_ERROR.getCode(), List.of(), "读取源文件失败");
        }
    }

    private static boolean shouldIncludeSourceFile(Path projectRoot, Path path) {
        Path relative = projectRoot.relativize(path);
        for (Path part : relative) {
            if (EXCLUDED_SOURCE_DIRS.contains(part.toString())) {
                return false;
            }
        }
        String fileName = path.getFileName().toString();
        if (TEXT_FILE_NAMES.contains(fileName)) {
            return true;
        }
        return TEXT_FILE_EXTENSIONS.contains(getExtension(fileName));
    }

    private static SourceFileVO toSourceFile(Path projectRoot, Path path) {
        String relativePath = toRelativePath(projectRoot, path);
        String fileName = path.getFileName().toString();
        try {
            return new SourceFileVO(
                    relativePath,
                    fileName,
                    Files.readString(path, StandardCharsets.UTF_8),
                    inferLang(fileName)
            );
        } catch (IOException e) {
            return new SourceFileVO(relativePath, fileName, "", inferLang(fileName));
        }
    }

    private static String toRelativePath(Path projectRoot, Path path) {
        return projectRoot.relativize(path).toString().replace(File.separatorChar, '/');
    }

    private static String getExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase();
    }

    private static String inferLang(String fileName) {
        String extension = getExtension(fileName);
        return switch (extension) {
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
            default -> "plaintext";
        };
    }

    /**
     * 提供静态资源访问，支持目录重定向
     * 访问格式：http://localhost:8123/api/static/{deployKey}[/{fileName}]
     */
    @GetMapping("/{deployKey}/**")
    public ResponseEntity<Resource> serveStaticResource(
            @PathVariable String deployKey,
            HttpServletRequest request) {
        try {
            // 获取资源路径
            String resourcePath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            resourcePath = resourcePath.substring(("/static/" + deployKey).length());
            // 如果是目录访问（不带斜杠），重定向到带斜杠的URL
            if (resourcePath.isEmpty()) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("Location", request.getRequestURI() + "/");
                return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
            }
            // 目录访问（含 /dist/ 等子目录）统一映射到 index.html
            if (resourcePath.endsWith("/")) {
                resourcePath = resourcePath + "index.html";
            } else if (resourcePath.equals("/")) {
                resourcePath = "/index.html";
            }
            // 构建文件路径
            String filePath = PREVIEW_ROOT_DIR + File.separator + deployKey + resourcePath;
            File file = new File(filePath);
            // 检查文件是否存在
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }
            // 返回文件资源
            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                    .header("Content-Type", getContentTypeWithCharset(filePath))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 根据文件扩展名返回带字符编码的 Content-Type
     */
    private String getContentTypeWithCharset(String filePath) {
        if (filePath.endsWith(".html")) return "text/html; charset=UTF-8";
        if (filePath.endsWith(".css")) return "text/css; charset=UTF-8";
        if (filePath.endsWith(".js") || filePath.endsWith(".mjs")) return "application/javascript; charset=UTF-8";
        if (filePath.endsWith(".json")) return "application/json; charset=UTF-8";
        if (filePath.endsWith(".png")) return "image/png";
        if (filePath.endsWith(".jpg") || filePath.endsWith(".jpeg")) return "image/jpeg";
        if (filePath.endsWith(".gif")) return "image/gif";
        if (filePath.endsWith(".svg")) return "image/svg+xml";
        if (filePath.endsWith(".ico")) return "image/x-icon";
        if (filePath.endsWith(".webp")) return "image/webp";
        if (filePath.endsWith(".woff")) return "font/woff";
        if (filePath.endsWith(".woff2")) return "font/woff2";
        if (filePath.endsWith(".ttf")) return "font/ttf";
        return "application/octet-stream";
    }
}
