package com.example.aicodemother.controller;

import cn.hutool.core.io.FileUtil;
import com.example.aicodemother.common.BaseResponse;
import com.example.aicodemother.constant.AppConstant;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class StaticResourceControllerTest {

    @Test
    void listSourceFilesReturnsProjectFilesWithoutBuildArtifactsOrDependencies() throws Exception {
        String deployKey = "vue_project_" + System.nanoTime();
        Path projectRoot = Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, deployKey);
        try {
            Files.createDirectories(projectRoot.resolve("src/pages"));
            Files.createDirectories(projectRoot.resolve("dist/assets"));
            Files.createDirectories(projectRoot.resolve("node_modules/vue"));
            Files.writeString(projectRoot.resolve("package.json"), "{\"scripts\":{\"build\":\"vite build\"}}", StandardCharsets.UTF_8);
            Files.writeString(projectRoot.resolve("src/pages/HomeView.vue"), "<template><main>Home</main></template>", StandardCharsets.UTF_8);
            Files.writeString(projectRoot.resolve("dist/index.html"), "<div>built</div>", StandardCharsets.UTF_8);
            Files.writeString(projectRoot.resolve("node_modules/vue/package.json"), "{}", StandardCharsets.UTF_8);

            StaticResourceController controller = new StaticResourceController();
            BaseResponse<List<StaticResourceController.SourceFileVO>> response =
                    controller.listSourceFiles(deployKey);

            Assertions.assertEquals(0, response.getCode());
            List<String> paths = response.getData().stream()
                    .map(StaticResourceController.SourceFileVO::path)
                    .toList();
            Assertions.assertTrue(paths.contains("package.json"));
            Assertions.assertTrue(paths.contains("src/pages/HomeView.vue"));
            Assertions.assertFalse(paths.contains("dist/index.html"));
            Assertions.assertFalse(paths.contains("node_modules/vue/package.json"));
        } finally {
            FileUtil.del(projectRoot);
        }
    }
}
