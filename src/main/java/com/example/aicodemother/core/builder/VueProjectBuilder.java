package com.example.aicodemother.core.builder;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * Vue 项目构建
 */
@Slf4j
@Component
public class VueProjectBuilder {

    /**
     * 异步构建项目（不阻塞主流程）
     *
     * @param projectPath 项目路径
     */
    public void buildProjectAsync(String projectPath) {
        // 在单独的线程中执行构建，避免阻塞主流程
        Thread.ofVirtual().name("vue-builder-" + System.currentTimeMillis()).start(() -> {
            try {
                boolean success = buildProject(projectPath);
                if (!success) {
                    log.error("异步构建 Vue 项目失败，项目路径: {}", projectPath);
                }
            } catch (Exception e) {
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
        // 验证 dist 目录是否生成
        File distDir = new File(projectDir, "dist");
        if (!distDir.exists()) {
            log.error("构建完成但 dist 目录未生成: {}", distDir.getAbsolutePath());
            return false;
        }
        log.info("Vue 项目构建成功，dist 目录: {}", distDir.getAbsolutePath());
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

}
