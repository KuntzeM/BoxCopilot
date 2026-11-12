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
        MockEnvironment environment = completeProd();

        assertThatCode(() -> new StartupGuard(environment).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    private static final String[] PROD_KEYS = {
            "spring.datasource.url", "spring.datasource.username", "spring.datasource.password",
            "spring.data.redis.host", "spring.data.redis.port", "spring.data.redis.password",
            "spring.security.oauth2.client.registration.nextcloud.client-id",
            "spring.security.oauth2.client.registration.nextcloud.client-secret",
            "spring.security.oauth2.client.registration.nextcloud.redirect-uri",
            "spring.security.oauth2.client.provider.nextcloud.issuer-uri",
            "nextcloud.logout-url", "frontend.url"};

    private static MockEnvironment completeProd() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        for (String key : PROD_KEYS) {
            environment.setProperty(key, "x");
        }
        environment.setProperty("spring.datasource.url", POSTGRES_URL);
        return environment;
    }

    @Test
    void prodReportsAllMissingSettingsInOneMessage() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        // application-prod.yml leaves an unresolvable placeholder behind when the variable is unset
        environment.setProperty("spring.data.redis.password", "${REDIS_PASSWORD}");
        environment.setProperty("frontend.url", "http://front");

        assertThatThrownBy(() -> new StartupGuard(environment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPRING_DATASOURCE_URL")
                .hasMessageContaining("REDIS_PASSWORD")
                .hasMessageContaining("CLIENT_ID")
                .hasMessageContaining("CLIENT_SECRET")
                .hasMessageContaining("NEXTCLOUD_URL")
                .hasMessageContaining("NEXTCLOUD_LOGOUT_URL")
                .hasMessageContaining("VITE_API_BASE_URL")
                .hasMessageNotContaining("FRONTEND_URL");
    }

    @Test
    void prodNamesASingleMissingSetting() {
        MockEnvironment environment = completeProd();
        environment.setProperty("nextcloud.logout-url", "${NEXTCLOUD_LOGOUT_URL}");

        assertThatThrownBy(() -> new StartupGuard(environment).afterPropertiesSet())
                .hasMessageContaining("NEXTCLOUD_LOGOUT_URL")
                .hasMessageNotContaining("CLIENT_ID");
    }

    @Test
    void devIgnoresTheProdRequirements() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");

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
