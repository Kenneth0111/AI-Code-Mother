package com.example.aicodemother.ai.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.example.aicodemother.constant.AppConstant;
import com.example.aicodemother.core.builder.VueProjectScaffold;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

class BatchFileWriteToolTest {

    @Test
    void generateToolExecutedResultReturnsSummaryWithoutFileContent() {
        BatchFileWriteTool tool = new BatchFileWriteTool();
        JSONObject arguments = new JSONObject();
        arguments.set("files", JSONUtil.toJsonStr(new Object[]{
                new JSONObject()
                        .set("path", "src/App.vue")
                        .set("content", "<template><SecretMarker /></template>"),
                new JSONObject()
                        .set("path", "package.json")
                        .set("content", "{\"scripts\":{\"build\":\"vite build\"}}")
        }));

        String result = tool.generateToolExecutedResult(arguments);

        Assertions.assertTrue(result.contains("batchWriteFiles completed"));
        Assertions.assertTrue(result.contains("src/App.vue"));
        Assertions.assertTrue(result.contains("package.json"));
        Assertions.assertTrue(result.contains("total_bytes="));
        Assertions.assertFalse(result.contains("SecretMarker"));
        Assertions.assertFalse(result.contains("vite build"));
    }

    @Test
    void batchWriteFilesEnsuresVueScaffoldBeforeWritingBusinessFiles() throws Exception {
        BatchFileWriteTool tool = new BatchFileWriteTool();
        ReflectionTestUtils.setField(tool, "vueProjectScaffold", new VueProjectScaffold());
        long appId = System.nanoTime();
        Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, "vue_project_" + appId);
        JSONObject page = new JSONObject()
                .set("path", "src/pages/HomeView.vue")
                .set("content", "<template><main>Home</main></template>");
        String files = JSONUtil.toJsonStr(new Object[]{page});

        try {
            tool.batchWriteFiles(files, appId);

            Assertions.assertTrue(Files.exists(projectRoot.resolve("package.json")));
            Assertions.assertTrue(Files.exists(projectRoot.resolve("vite.config.js")));
            Assertions.assertTrue(Files.exists(projectRoot.resolve("index.html")));
            Assertions.assertTrue(Files.exists(projectRoot.resolve("src/main.js")));
            Assertions.assertTrue(Files.exists(projectRoot.resolve("src/App.vue")));
            Assertions.assertTrue(Files.exists(projectRoot.resolve("src/router/index.js")));
            Assertions.assertTrue(Files.exists(projectRoot.resolve("src/pages/HomeView.vue")));
        } finally {
            FileUtil.del(projectRoot);
        }
    }
}
