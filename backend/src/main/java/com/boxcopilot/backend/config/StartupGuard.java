package com.boxcopilot.backend.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * Aborts application startup when the permissive {@code test} profile is combined with
 * production settings, so that a misconfiguration can never expose an unauthenticated instance.
 * <p>
 * In {@code prod} it also reports every required setting that is missing in ONE message. The check is a
 * {@link BeanFactoryPostProcessor} so that it runs before any other bean: otherwise the first bean that needs a
 * missing value fails with an unresolved-placeholder error that names only that one value ({@link Early}).
 */
@Component
public class StartupGuard implements InitializingBean {

    private static final String TEST_PROFILE = "test";
    private static final String PROD_PROFILE = "prod";
    private static final String POSTGRES_URL_PREFIX = "jdbc:postgresql:";

    private static final String NEXTCLOUD = "spring.security.oauth2.client.registration.nextcloud.";

    /**
     * Environment variable named in the error message -> the property of application-prod.yml it feeds. The
     * property is checked, not the variable, so a value supplied any other way (system property, test property)
     * counts. Empty is accepted (compose enforces a non-empty REDIS_PASSWORD with {@code :?}); an unset variable
     * leaves an unresolvable placeholder behind, which is what is detected here.
     */
    private static final Map<String, String> REQUIRED_IN_PROD = new LinkedHashMap<>();

    static {
        REQUIRED_IN_PROD.put("SPRING_DATASOURCE_URL", "spring.datasource.url");
        REQUIRED_IN_PROD.put("POSTGRES_USER", "spring.datasource.username");
        REQUIRED_IN_PROD.put("POSTGRES_PASSWORD", "spring.datasource.password");
        REQUIRED_IN_PROD.put("REDIS_HOST", "spring.data.redis.host");
        REQUIRED_IN_PROD.put("REDIS_PORT", "spring.data.redis.port");
        REQUIRED_IN_PROD.put("REDIS_PASSWORD", "spring.data.redis.password");
        REQUIRED_IN_PROD.put("CLIENT_ID", NEXTCLOUD + "client-id");
        REQUIRED_IN_PROD.put("CLIENT_SECRET", NEXTCLOUD + "client-secret");
        REQUIRED_IN_PROD.put("VITE_API_BASE_URL", NEXTCLOUD + "redirect-uri");
        REQUIRED_IN_PROD.put("NEXTCLOUD_LOGOUT_URL", "nextcloud.logout-url");
        REQUIRED_IN_PROD.put("FRONTEND_URL", "frontend.url");
    }

    private final Environment environment;

    public StartupGuard(Environment environment) {
        this.environment = environment;
    }

    /** Runs the same check before any other bean is created; a bean factory post-processor cannot take constructor arguments. */
    @Component
    static class Early implements BeanFactoryPostProcessor, EnvironmentAware {
        private Environment environment;

        @Override
        public void setEnvironment(Environment environment) {
            this.environment = environment;
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
            new StartupGuard(environment).afterPropertiesSet();
        }
    }

    private boolean isMissing(String key) {
        try {
            return environment.getProperty(key) == null;
        } catch (IllegalArgumentException unresolvablePlaceholder) {
            return true;
        }
    }

    /** A leaked dev profile or default would otherwise run production on an H2 file with the well-known admin password. */
    private void rejectDevSettingsInProd() {
        if (environment.acceptsProfiles(Profiles.of("dev"))) {
            throw new IllegalStateException("Profile 'dev' must not be active together with '" + PROD_PROFILE + "'");
        }
        String url = environment.getProperty("spring.datasource.url", "");
        String lower = url.toLowerCase(Locale.ROOT);
        // in-memory H2 persists nothing (the prod-profile web tests use it); a file database is the real risk
        if (lower.startsWith("jdbc:h2:") && !lower.startsWith("jdbc:h2:mem:")) {
            throw new IllegalStateException("Profile '" + PROD_PROFILE + "' must not use a file-based H2 datasource");
        }
        if ("admin".equals(environment.getProperty("app.admin.password"))) {
            throw new IllegalStateException(
                    "Profile '" + PROD_PROFILE + "' must not use the default admin password (set ADMIN_PASSWORD)");
        }
    }

    @Override
    public void afterPropertiesSet() {
        if (environment.acceptsProfiles(Profiles.of(PROD_PROFILE))) {
            List<String> missing = new ArrayList<>();
            REQUIRED_IN_PROD.forEach((name, key) -> {
                if (isMissing(key)) {
                    missing.add(name);
                }
            });
            // The issuer only matters while the registration uses the 'nextcloud' provider (the default)
            String provider = environment.getProperty(NEXTCLOUD + "provider", "nextcloud");
            if ("nextcloud".equals(provider)
                    && isMissing("spring.security.oauth2.client.provider.nextcloud.issuer-uri")) {
                missing.add("NEXTCLOUD_URL");
            }
            if (!missing.isEmpty() && !environment.acceptsProfiles(Profiles.of(TEST_PROFILE))) {
                throw new IllegalStateException(
                        "Missing required configuration for profile 'prod': " + String.join(", ", missing));
            }
            if (!environment.acceptsProfiles(Profiles.of(TEST_PROFILE))) {
                rejectDevSettingsInProd();
            }
        }
        if (!environment.acceptsProfiles(Profiles.of(TEST_PROFILE))) {
            return;
        }
        if (environment.acceptsProfiles(Profiles.of(PROD_PROFILE))) {
            throw new IllegalStateException(
                    "Profile '" + TEST_PROFILE + "' must not be active together with '" + PROD_PROFILE + "'");
        }
        String datasourceUrl = environment.getProperty("spring.datasource.url", "");
        if (datasourceUrl.toLowerCase(Locale.ROOT).startsWith(POSTGRES_URL_PREFIX)) {
            throw new IllegalStateException(
                    "Profile '" + TEST_PROFILE + "' must not be used with a Postgres datasource");
        }
    }
}
