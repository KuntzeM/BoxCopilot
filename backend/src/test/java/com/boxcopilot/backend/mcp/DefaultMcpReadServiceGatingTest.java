package com.boxcopilot.backend.mcp;

import com.boxcopilot.backend.repository.BoxRepository;
import com.boxcopilot.backend.repository.ItemRepository;
import com.boxcopilot.backend.service.ImageStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DefaultMcpReadServiceGatingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(ItemRepository.class, () -> mock(ItemRepository.class))
            .withBean(BoxRepository.class, () -> mock(BoxRepository.class))
            .withBean(ImageStorageService.class, () -> mock(ImageStorageService.class))
            .withUserConfiguration(DefaultMcpReadService.class);

    @Test
    void serviceDoesNotExistWhenFlagIsMissing() {
        runner.run(context -> assertThat(context).doesNotHaveBean(McpReadService.class));
    }

    @Test
    void serviceDoesNotExistWhenFlagIsFalse() {
        runner.withPropertyValues("app.mcp.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(McpReadService.class));
    }

    @Test
    void serviceExistsWhenFlagIsTrue() {
        runner.withPropertyValues("app.mcp.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(McpReadService.class));
    }
}
