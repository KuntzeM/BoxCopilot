package com.boxcopilot.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import org.springframework.data.redis.serializer.RedisSerializer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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

    @Test
    void sessionSerializerBeanIsRegisteredUnderTheNameSpringSessionLooksUp() {
        runner.run(context -> assertThat(context).hasBean("springSessionDefaultRedisSerializer"));
    }

    @Test
    void sessionSerializerRoundTripsNormalValues() {
        RedisSerializer<Object> serializer = RedisConfig.tolerantSerializer(getClass().getClassLoader());

        assertThat(serializer.deserialize(serializer.serialize(new ArrayList<>(List.of("a", 1L)))))
                .isEqualTo(List.of("a", 1L));
    }

    @Test
    void sessionSerializerReadsUnreadableBytesAsNull() {
        RedisSerializer<Object> serializer = RedisConfig.tolerantSerializer(getClass().getClassLoader());

        assertThat(serializer.deserialize("not a java stream".getBytes(StandardCharsets.UTF_8))).isNull();
        assertThat(serializer.deserialize(new byte[]{(byte) 0xAC, (byte) 0xED, 0, 5, 0x73, 0x72, 0, 1, 'X'})).isNull();
    }
}
