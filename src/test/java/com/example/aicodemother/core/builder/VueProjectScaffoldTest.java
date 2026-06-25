package com.example.aicodemother.core.builder;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class VueProjectScaffoldTest {

    @TempDir
    Path tempDir;

    @Test
    void ensureScaffoldCreatesStableVueInfrastructureFiles() {
        Path projectRoot = tempDir.resolve("vue_project_1");
        VueProjectScaffold scaffold = new VueProjectScaffold();

        scaffold.ensureScaffold(projectRoot);

        Assertions.assertTrue(Files.exists(projectRoot.resolve("package.json")));
        Assertions.assertTrue(Files.exists(projectRoot.resolve("vite.config.js")));
        Assertions.assertTrue(Files.exists(projectRoot.resolve("index.html")));
        Assertions.assertTrue(Files.exists(projectRoot.resolve("src/main.js")));
        Assertions.assertTrue(Files.exists(projectRoot.resolve("src/App.vue")));
        Assertions.assertTrue(Files.exists(projectRoot.resolve("src/router/index.js")));
    }

    @Test
    void ensureScaffoldDoesNotOverwriteExistingFiles() throws Exception {
        Path projectRoot = tempDir.resolve("vue_project_2");
        Path appVue = projectRoot.resolve("src/App.vue");
        Files.createDirectories(appVue.getParent());
        Files.writeString(appVue, "<template><main>custom</main></template>");
        VueProjectScaffold scaffold = new VueProjectScaffold();

        scaffold.ensureScaffold(projectRoot);

        Assertions.assertEquals("<template><main>custom</main></template>", Files.readString(appVue));
    }
}
