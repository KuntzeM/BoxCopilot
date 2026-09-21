package com.boxcopilot.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;

class RedisConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RedisConfig.class)
            .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
            .withPropertyValues("spring.data.redis.host=redis.internal", "spring.data.redis.port=6380");

    @Test
    void connectionFactoryCarriesConfiguredPassword() {
        runner.withPropertyValues("spring.data.redis.password=s3cret-redis-pw")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    LettuceConnectionFactory factory = context.getBean(LettuceConnectionFactory.class);
                    assertThat(factory.getHostName()).isEqualTo("redis.internal");
                    assertThat(factory.getPort()).isEqualTo(6380);
                    assertThat(factory.getStandaloneConfiguration().getPassword())
                            .isEqualTo(RedisPassword.of("s3cret-redis-pw"));
                });
    }

    @Test
    void connectionFactoryHasNoPasswordWhenNoneConfigured() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            LettuceConnectionFactory factory = context.getBean(LettuceConnectionFactory.class);
            assertThat(factory.getStandaloneConfiguration().getPassword().isPresent()).isFalse();
        });
    }
}
