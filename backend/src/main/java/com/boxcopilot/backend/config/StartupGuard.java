package com.boxcopilot.backend.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Aborts application startup when the permissive {@code test} profile is combined with
 * production settings, so that a misconfiguration can never expose an unauthenticated instance.
 */
@Component
public class StartupGuard implements InitializingBean {

    private static final String TEST_PROFILE = "test";
    private static final String PROD_PROFILE = "prod";
    private static final String POSTGRES_URL_PREFIX = "jdbc:postgresql:";

    private final Environment environment;

    public StartupGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
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
