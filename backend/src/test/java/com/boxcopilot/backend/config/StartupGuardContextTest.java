package com.boxcopilot.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class StartupGuardContextTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(StartupGuard.class);

    @Test
    void contextFailsToStartWhenTestAndProdProfilesAreActive() {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("test", "prod"))
                .run(context -> {
                    assertThat(context).hasFailed();
                    // StartupGuard.Early fails the context before any bean exists, so the failure is not wrapped
                    assertThat(context.getStartupFailure())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("test")
                            .hasMessageContaining("prod");
                });
    }

    @Test
    void contextStartsWithTestProfileAlone() {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("test"))
                .run(context -> assertThat(context).hasNotFailed());
    }
}
