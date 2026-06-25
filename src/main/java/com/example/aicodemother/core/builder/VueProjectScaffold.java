package com.example.aicodemother.core.builder;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Creates the stable Vue/Vite project infrastructure so the LLM only writes app-specific files.
 */
@Slf4j
@Component
public class VueProjectScaffold {

    public void ensureScaffold(Path projectRoot) {
        try {
            Files.createDirectories(projectRoot);
            writeIfMissing(projectRoot.resolve("package.json"), """
                    {
                      "name": "vue-app",
                      "private": true,
                      "version": "0.0.0",
                      "type": "module",
                      "scripts": {
                        "dev": "vite",
                        "build": "vite build"
                      },
                      "dependencies": {
                        "vue": "^3.3.4",
                        "vue-router": "^4.2.4"
                      },
                      "devDependencies": {
                        "@vitejs/plugin-vue": "^4.2.3",
                        "vite": "^4.4.5"
                      }
                    }
                    """);
            writeIfMissing(projectRoot.resolve("vite.config.js"), """
                    import { defineConfig } from 'vite'
                    import vue from '@vitejs/plugin-vue'
                    import { fileURLToPath } from 'url'

                    export default defineConfig({
                      base: './',
                      plugins: [vue()],
                      resolve: {
                        alias: {
                          '@': fileURLToPath(new URL('./src', import.meta.url))
                        }
                      }
                    })
                    """);
            writeIfMissing(projectRoot.resolve("index.html"), """
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
                    """);
            writeIfMissing(projectRoot.resolve("src/main.js"), """
                    import { createApp } from 'vue'
                    import App from './App.vue'
                    import router from './router'

                    const app = createApp(App)
                    app.use(router)
                    app.mount('#app')
                    """);
            writeIfMissing(projectRoot.resolve("src/App.vue"), """
                    <template>
                      <router-view />
                    </template>

                    <script setup>
                    </script>

                    <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; }
                    </style>
                    """);
            writeIfMissing(projectRoot.resolve("src/router/index.js"), """
                    import { createRouter, createWebHashHistory } from 'vue-router'
                    import HomeView from '@/pages/HomeView.vue'

                    const routes = [
                      { path: '/', component: HomeView }
                    ]

                    const router = createRouter({
                      history: createWebHashHistory(),
                      routes
                    })

                    export default router
                    """);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create Vue project scaffold: " + projectRoot, e);
        }
    }

    private void writeIfMissing(Path path, String content) throws IOException {
        if (Files.exists(path)) {
            return;
        }
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, content, StandardCharsets.UTF_8);
        log.info("Created Vue scaffold file: {}", path);
    }
}
