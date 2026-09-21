package com.boxcopilot.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StartupGuardTest {

    private static final String H2_URL = "jdbc:h2:mem:testdb";
    private static final String POSTGRES_URL = "jdbc:postgresql://db:5432/boxpilot";

    @Test
    void failsWhenTestProfileIsCombinedWithProd() {
        MockEnvironment environment = environment(H2_URL, "test", "prod");

        assertThatThrownBy(() -> new StartupGuard(environment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test")
                .hasMessageContaining("prod");
    }

    @Test
    void failsWhenTestProfileIsCombinedWithPostgresDatasource() {
        MockEnvironment environment = environment(POSTGRES_URL, "test");

        assertThatThrownBy(() -> new StartupGuard(environment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Postgres");
    }

    @Test
    void failsWhenPostgresUrlIsWrittenInMixedCase() {
        MockEnvironment environment = environment("JDBC:PostgreSQL://db/boxpilot", "test");

        assertThatThrownBy(() -> new StartupGuard(environment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void allowsTestProfileWithInMemoryDatabase() {
        MockEnvironment environment = environment(H2_URL, "test");

        assertThatCode(() -> new StartupGuard(environment).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    void allowsTestProfileWithoutDatasourceUrl() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");

        assertThatCode(() -> new StartupGuard(environment).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    void allowsProdProfileWithPostgresDatasource() {
        MockEnvironment environment = environment(POSTGRES_URL, "prod");

        assertThatCode(() -> new StartupGuard(environment).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    void allowsDevProfile() {
        MockEnvironment environment = environment("jdbc:h2:file:./data/boxcopilot", "dev");

        assertThatCode(() -> new StartupGuard(environment).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    private static MockEnvironment environment(String datasourceUrl, String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        environment.setProperty("spring.datasource.url", datasourceUrl);
        return environment;
    }
}
