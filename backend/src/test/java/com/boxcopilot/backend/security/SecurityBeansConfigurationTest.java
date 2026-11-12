package com.boxcopilot.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityBeansConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner();

    @Test
    void providesClockAndThrottleWithDefaults() {
        runner.withUserConfiguration(SecurityBeansConfiguration.class).run(ctx -> {
            assertThat(ctx).hasSingleBean(Clock.class).hasSingleBean(LoginThrottle.class);
            LoginThrottleProperties p = ctx.getBean(LoginThrottleProperties.class);
            assertThat(p.ipLimit()).isEqualTo(10);
            assertThat(p.ipUserLimit()).isEqualTo(5);
            assertThat(p.window().toMinutes()).isEqualTo(15);
        });
    }

    @Test
    void bindsConfiguredLimits() {
        runner.withUserConfiguration(SecurityBeansConfiguration.class)
                .withPropertyValues("app.security.login-throttle.ip-limit=3",
                        "app.security.login-throttle.ip-user-limit=2",
                        "app.security.login-throttle.window=30s")
                .run(ctx -> {
                    LoginThrottleProperties p = ctx.getBean(LoginThrottleProperties.class);
                    assertThat(p.ipLimit()).isEqualTo(3);
                    assertThat(p.ipUserLimit()).isEqualTo(2);
                    assertThat(p.window().toSeconds()).isEqualTo(30);
                });
    }

    @Test
    void anExistingClockWins() {
        runner.withUserConfiguration(OwnClock.class, SecurityBeansConfiguration.class)
                .run(ctx -> assertThat(ctx.getBean(Clock.class)).isSameAs(OwnClock.FIXED));
    }

    /** Mirrors McpConfiguration's fallback clock: whichever of the two is registered second must back off. */
    @Test
    void theMcpFallbackClockAndOursNeverBothRegister() {
        runner.withUserConfiguration(SecurityBeansConfiguration.class, McpLikeClock.class)
                .run(ctx -> assertThat(ctx).hasSingleBean(Clock.class));
        runner.withUserConfiguration(McpLikeClock.class, SecurityBeansConfiguration.class)
                .run(ctx -> assertThat(ctx).hasSingleBean(Clock.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class McpLikeClock {
        @Bean
        @ConditionalOnMissingBean(Clock.class)
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class OwnClock {
        static final Clock FIXED = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);

        @Bean
        Clock ownClock() {
            return FIXED;
        }
    }
}
