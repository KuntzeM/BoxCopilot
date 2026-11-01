package com.boxcopilot.backend.config;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.session.autoconfigure.SessionAutoConfiguration;
import org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisAutoConfiguration;
import org.springframework.boot.session.jdbc.autoconfigure.JdbcSessionAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.redis.RedisSessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boot does not reject a {@code spring.autoconfigure.exclude} entry whose class does not exist, it ignores it,
 * so a renamed auto-configuration silently stops being excluded. These tests load the real profile YAML, apply
 * its exclude list to the Redis / session / JDBC auto-configurations and check what is left running.
 */
class ProfileExclusionsTest {

    private static final Class<?>[] CANDIDATES = {
            DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            DataRedisAutoConfiguration.class, DataRedisRepositoriesAutoConfiguration.class,
            SessionAutoConfiguration.class, SessionDataRedisAutoConfiguration.class, JdbcSessionAutoConfiguration.class};

    @AutoConfigurationPackage
    static class Packages {
    }

    private WebApplicationContextRunner runner(String profile) throws IOException {
        List<String> excluded = excludedBy("application-" + profile + ".yml");
        excluded.forEach(name -> assertThat(isLoadable(name))
                .as("excluded class %s must exist, Boot ignores unknown names", name).isTrue());
        Class<?>[] kept = Arrays.stream(CANDIDATES).filter(c -> !excluded.contains(c.getName()))
                .toArray(Class<?>[]::new);
        return new WebApplicationContextRunner()
                .withUserConfiguration(Packages.class)
                .withConfiguration(AutoConfigurations.of(kept))
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=" + profile,
                        "spring.datasource.url=jdbc:h2:mem:" + profile + ";DB_CLOSE_DELAY=-1",
                        "spring.datasource.driverClassName=org.h2.Driver",
                        "spring.datasource.username=sa", "spring.datasource.password=");
    }

    private static List<String> excludedBy(String yaml) throws IOException {
        PropertySource<?> source = new YamlPropertySourceLoader()
                .load(yaml, new ClassPathResource(yaml)).get(0);
        return Arrays.asList(new Binder(new MapConfigurationPropertySource(
                ((java.util.Map<String, Object>) source.getSource())))
                .bind("spring.autoconfigure.exclude", String[].class).orElse(new String[0]));
    }

    private static boolean isLoadable(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Test
    void devHasNoRedis() throws IOException {
        runner("dev").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(RedisConnectionFactory.class);
            assertThat(context).hasSingleBean(JdbcIndexedSessionRepository.class);
        });
    }

    @Test
    void prodKeepsSessionsInRedis() throws IOException {
        runner("prod").withPropertyValues("spring.data.redis.host=localhost", "spring.data.redis.port=6379").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(RedisSessionRepository.class);
            assertThat(context).doesNotHaveBean(JdbcIndexedSessionRepository.class);
        });
    }

    @Test
    void testProfileHasNoSessions() throws IOException {
        runner("test").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(SessionRepository.class);
        });
    }

    @Test
    void mcpTestProfileHasNoSessionsAndNoRedis() throws IOException {
        runner("mcp-test").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(SessionRepository.class);
            assertThat(context).doesNotHaveBean(RedisConnectionFactory.class);
        });
    }
}
