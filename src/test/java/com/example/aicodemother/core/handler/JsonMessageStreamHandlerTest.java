package com.example.aicodemother.core.handler;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.example.aicodemother.ai.model.message.AiResponseMessage;
import com.example.aicodemother.constant.AppConstant;
import com.example.aicodemother.core.builder.VueProjectBuilder;
import com.example.aicodemother.model.entity.User;
import com.example.aicodemother.service.ChatHistoryService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

class JsonMessageStreamHandlerTest {

    @Test
    void handleDoesNotStartVueBuildWhenProjectDirectoryWasNeverCreated() {
        JsonMessageStreamHandler handler = new JsonMessageStreamHandler();
        CapturingVueProjectBuilder vueProjectBuilder = new CapturingVueProjectBuilder();
        ReflectionTestUtils.setField(handler, "vueProjectBuilder", vueProjectBuilder);
        ChatHistoryService chatHistoryService = Mockito.mock(ChatHistoryService.class);
        long appId = System.nanoTime();
        Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, "vue_project_" + appId);
        FileUtil.del(projectRoot);
        User loginUser = User.builder().id(1L).build();
        Flux<String> originFlux = Flux.just(JSONUtil.toJsonStr(
                new AiResponseMessage("\n\n[系统提示] 工具调用次数已达上限，操作已结束。")));

        try {
            handler.handle(originFlux, chatHistoryService, appId, loginUser).collectList().block();

            Assertions.assertTrue(vueProjectBuilder.projectPaths.isEmpty());
        } finally {
            FileUtil.del(projectRoot);
        }
    }

    @Test
    void handleStartsVueBuildWhenProjectDirectoryExists() {
        JsonMessageStreamHandler handler = new JsonMessageStreamHandler();
        CapturingVueProjectBuilder vueProjectBuilder = new CapturingVueProjectBuilder();
        ReflectionTestUtils.setField(handler, "vueProjectBuilder", vueProjectBuilder);
        ChatHistoryService chatHistoryService = Mockito.mock(ChatHistoryService.class);
        long appId = System.nanoTime();
        Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, "vue_project_" + appId);
        User loginUser = User.builder().id(1L).build();
        Flux<String> originFlux = Flux.just(JSONUtil.toJsonStr(new AiResponseMessage("done")));

        try {
            FileUtil.mkdir(projectRoot);

            handler.handle(originFlux, chatHistoryService, appId, loginUser).collectList().block();

            Assertions.assertEquals(1, vueProjectBuilder.projectPaths.size());
            Assertions.assertEquals(projectRoot, Paths.get(vueProjectBuilder.projectPaths.getFirst()));
        } finally {
            FileUtil.del(projectRoot);
        }
    }

    private static class CapturingVueProjectBuilder extends VueProjectBuilder {

        private final List<String> projectPaths = new ArrayList<>();

        @Override
        public void buildProjectAsync(String projectPath, Long appId) {
            projectPaths.add(projectPath);
        }
    }
}
