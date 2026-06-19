package com.example.aicodemother.core.builder;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Vue 项目构建
 */
@Slf4j
@Component
public class VueProjectBuilder {

    public enum BuildStatus {
        BUILDING, SUCCESS, FAILED
    }

    private final Map<Long, BuildStatus> buildStatusMap = new ConcurrentHashMap<>();

    /**
     * 查询构建状态
     */
    public BuildStatus getBuildStatus(Long appId) {
        return buildStatusMap.get(appId);
    }

    /**
     * 异步构建项目（不阻塞主流程）
     *
     * @param projectPath 项目路径
     * @param appId       应用ID，用于跟踪构建状态
     */
    public void buildProjectAsync(String projectPath, Long appId) {
        buildStatusMap.put(appId, BuildStatus.BUILDING);
        Thread.ofVirtual().name("vue-builder-" + appId).start(() -> {
            try {
                boolean success = buildProject(projectPath);
                buildStatusMap.put(appId, success ? BuildStatus.SUCCESS : BuildStatus.FAILED);
                if (!success) {
                    log.error("异步构建 Vue 项目失败，项目路径: {}", projectPath);
                }
            } catch (Exception e) {
                buildStatusMap.put(appId, BuildStatus.FAILED);
                log.error("异步构建 Vue 项目时发生异常: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * 构建 Vue 项目
     *
     * @param projectPath 项目根目录路径
     * @return 是否构建成功
     */
    public boolean buildProject(String projectPath) {
        File projectDir = new File(projectPath);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            log.error("项目目录不存在: {}", projectPath);
            return false;
        }
        // 检查 package.json 是否存在
        File packageJson = new File(projectDir, "package.json");
        if (!packageJson.exists()) {
            log.error("package.json 文件不存在: {}", packageJson.getAbsolutePath());
            return false;
        }
        log.info("开始构建 Vue 项目: {}", projectPath);
        // 构建前自动补充关键入口文件
        repairEntryFiles(projectDir);
        // 构建前自动修复：为缺失的本地 CSS / Vue 组件 import 创建占位文件，避免 AI 漏建文件导致构建失败
        repairMissingImports(projectDir);
        // 执行 npm install
        if (!executeNpmInstall(projectDir)) {
            log.error("npm install 执行失败");
            return false;
        }
        // 执行 npm run build
        if (!executeNpmBuild(projectDir)) {
            log.error("npm run build 执行失败");
            return false;
        }
        // 验证构建产物有效性
        if (!validateBuildOutput(projectDir)) {
            return false;
        }
        log.info("Vue 项目构建成功，dist 目录: {}", new File(projectDir, "dist").getAbsolutePath());
        return true;
    }

    /**
     * 执行 npm install 命令
     */
    private boolean executeNpmInstall(File projectDir) {
        log.info("执行 npm install...");
        return executeCommand(projectDir, new String[]{"npm", "install"}, 300);
    }

    /**
     * 执行 npm run build 命令
     */
    private boolean executeNpmBuild(File projectDir) {
        log.info("执行 npm run build...");
        return executeCommand(projectDir, new String[]{"npm", "run", "build"}, 180);
    }

    /**
     * 操作系统检测
     *
     * @return 是否Windows系统
     */
    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    /**
     * 执行命令
     *
     * @param workingDir     工作目录
     * @param args           命令及参数数组
     * @param timeoutSeconds 超时时间（秒）
     * @return 是否执行成功
     */
    private boolean executeCommand(File workingDir, String[] args, int timeoutSeconds) {
        // Windows 下需要通过 cmd /c 执行
        String[] fullCmd;
        if (isWindows()) {
            fullCmd = new String[args.length + 2];
            fullCmd[0] = "cmd";
            fullCmd[1] = "/c";
            System.arraycopy(args, 0, fullCmd, 2, args.length);
        } else {
            fullCmd = args;
        }
        try {
            log.info("在目录 {} 中执行命令: {}", workingDir.getAbsolutePath(), String.join(" ", fullCmd));
            ProcessBuilder pb = new ProcessBuilder(fullCmd);
            pb.directory(workingDir);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            // 必须消费输出流，否则缓冲区满时子进程会阻塞
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("[npm] {}", line);
                    output.append(line).append("\n");
                }
            }
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                log.error("命令执行超时（{}秒），强制终止进程", timeoutSeconds);
                process.destroyForcibly();
                return false;
            }
            int exitCode = process.exitValue();
            if (exitCode == 0) {
                log.info("命令执行成功: {}", String.join(" ", fullCmd));
                return true;
            } else {
                log.error("命令执行失败，退出码: {}，输出:\n{}", exitCode, output);
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("执行命令时被中断: {}", String.join(" ", fullCmd), e);
            return false;
        } catch (Exception e) {
            log.error("执行命令失败: {}, 错误信息: {}", String.join(" ", fullCmd), e.getMessage(), e);
            return false;
        }
    }

    // 静态 import（含 from）：import Foo from './Foo.vue' 或 import { x } from '@/utils/x'
    private static final Pattern STATIC_IMPORT_PATTERN = Pattern.compile(
            "from\\s+['\"]((\\.\\.?/|@/)[^'\"]+)['\"]");
    // 副作用 import：import './assets/global.css' 或 import '@/styles/main.css'
    private static final Pattern SIDE_EFFECT_IMPORT_PATTERN = Pattern.compile(
            "import\\s+['\"]((\\.\\.?/|@/)[^'\"]+)['\"]");
    // 动态 import：() => import('./pages/Home.vue') 或 import('@/pages/About.vue')
    private static final Pattern DYNAMIC_IMPORT_PATTERN = Pattern.compile(
            "import\\s*\\(\\s*['\"]((\\.\\.?/|@/)[^'\"]+)['\"]\\s*\\)");

    /**
     * 构建前自动修复：扫描 .js / .vue 文件中引用的本地文件，
     * 为缺失的 CSS / Vue 组件 import 创建占位文件，避免 AI 漏建文件导致 vite build 失败。
     */
    private void repairMissingImports(File projectDir) {
        File srcDir = new File(projectDir, "src");
        if (!srcDir.exists()) {
            return;
        }
        try (Stream<Path> paths = Files.walk(srcDir.toPath())) {
            List<Path> sourceFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".js") || name.endsWith(".vue");
                    })
                    .toList();
            for (Path sourceFile : sourceFiles) {
                String content = Files.readString(sourceFile, StandardCharsets.UTF_8);
                Set<String> importPaths = new java.util.LinkedHashSet<>();
                extractImportPaths(STATIC_IMPORT_PATTERN, content, importPaths);
                extractImportPaths(SIDE_EFFECT_IMPORT_PATTERN, content, importPaths);
                extractImportPaths(DYNAMIC_IMPORT_PATTERN, content, importPaths);
                for (String importPath : importPaths) {
                    File target = resolveImportPath(srcDir, sourceFile.toFile(), importPath);
                    if (target != null && !target.exists()) {
                        createStubFile(target);
                    }
                }
            }
        } catch (IOException e) {
            log.error("构建前修复 import 时出错: {}", e.getMessage(), e);
        }
    }

    private void extractImportPaths(Pattern pattern, String content, Set<String> results) {
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String path = matcher.group(1);
            // 只处理本地文件（.css / .vue），跳过 .js 模块（导出结构未知）
            if (path.endsWith(".css") || path.endsWith(".vue")) {
                results.add(path);
            } else if (!path.contains(".")) {
                // 无扩展名的 import，尝试当作 .vue 组件处理
                results.add(path + ".vue");
            }
        }
    }

    /**
     * 为缺失的被引用文件创建占位内容。CSS 补空注释，Vue 补最小可渲染组件。
     */
    private void createStubFile(File target) {
        try {
            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            String name = target.getName();
            String stub;
            if (name.endsWith(".vue")) {
                String componentName = name.substring(0, name.length() - 4);
                stub = "<template>\n  <!-- 自动生成的占位组件：" + componentName + " -->\n  <div></div>\n</template>\n\n"
                        + "<script setup>\n</script>\n\n<style scoped>\n</style>\n";
            } else {
                stub = "/* 自动生成的样式文件占位 */\n";
            }
            Files.writeString(target.toPath(), stub, StandardCharsets.UTF_8);
            log.warn("构建前修复：创建缺失的引用文件 {}", target.getAbsolutePath());
        } catch (IOException e) {
            log.error("创建占位文件失败 {}: {}", target.getAbsolutePath(), e.getMessage());
        }
    }

    /**
     * 解析 import 路径为实际文件。
     * @ 别名指向 src 目录；相对路径基于引用文件所在目录。
     */
    private File resolveImportPath(File srcDir, File sourceFile, String importPath) {
        if (importPath.startsWith("@/")) {
            return new File(srcDir, importPath.substring(2));
        }
        return new File(sourceFile.getParentFile(), importPath);
    }

    /**
     * 构建前补充关键入口文件：如果 AI 遗漏了 index.html 或 src/main.js，使用标准模板自动补充。
     */
    private void repairEntryFiles(File projectDir) {
        File indexHtml = new File(projectDir, "index.html");
        if (!indexHtml.exists()) {
            log.warn("构建前修复：缺少 index.html，自动补充标准模板");
            String template = """
                    <!DOCTYPE html>
                    <html lang="zh-CN">
                      <head>
                        <meta charset="UTF-8" />
                        <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                        <title>Vue App</title>
                      </head>
                      <body>
                        <div id="app"></div>
                        <script type="module" src="/src/main.js"></script>
                      </body>
                    </html>
                    """;
            try {
                Files.writeString(indexHtml.toPath(), template, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error("补充 index.html 失败: {}", e.getMessage());
            }
        }
        File srcDir = new File(projectDir, "src");
        File mainJs = new File(srcDir, "main.js");
        if (!mainJs.exists()) {
            log.warn("构建前修复：缺少 src/main.js，自动补充标准模板");
            String template = """
                    import { createApp } from 'vue'
                    import App from './App.vue'

                    const app = createApp(App)
                    app.mount('#app')
                    """;
            try {
                if (!srcDir.exists()) {
                    srcDir.mkdirs();
                }
                Files.writeString(mainJs.toPath(), template, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error("补充 src/main.js 失败: {}", e.getMessage());
            }
        }
        File appVue = new File(srcDir, "App.vue");
        if (!appVue.exists()) {
            log.warn("构建前修复：缺少 src/App.vue，自动补充标准模板");
            String template = """
                    <template>
                      <div id="app">
                        <router-view />
                      </div>
                    </template>

                    <script setup>
                    </script>

                    <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    </style>
                    """;
            try {
                Files.writeString(appVue.toPath(), template, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error("补充 src/App.vue 失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 验证构建产物：dist/index.html 必须存在且包含 script 标签。
     */
    private boolean validateBuildOutput(File projectDir) {
        File distDir = new File(projectDir, "dist");
        if (!distDir.exists()) {
            log.error("构建完成但 dist 目录未生成: {}", distDir.getAbsolutePath());
            return false;
        }
        File distIndex = new File(distDir, "index.html");
        if (!distIndex.exists()) {
            log.error("构建完成但 dist/index.html 不存在");
            return false;
        }
        try {
            String htmlContent = Files.readString(distIndex.toPath(), StandardCharsets.UTF_8);
            if (!htmlContent.contains("<script")) {
                log.error("dist/index.html 不包含 script 标签，构建产物可能为空");
                return false;
            }
        } catch (IOException e) {
            log.error("读取 dist/index.html 失败: {}", e.getMessage());
            return false;
        }
        return true;
    }

}
