package com.boxcopilot.backend.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * A blank {@code TRUSTED_PROXY_PATTERN} (e.g. the old {@code TRUSTED_PROXY_PATTERN=} line of .env.example) is a
 * defined value for Spring, so the placeholder default of {@code server.tomcat.remoteip.internal-proxies} would not
 * apply: Tomcat then trusts no proxy, every client shares the proxy address and the login throttle blocks everybody.
 * Blank is treated as unset by supplying the default as the variable's value.
 */
public final class TrustedProxyDefault implements EnvironmentPostProcessor {

    static final String VARIABLE = "TRUSTED_PROXY_PATTERN";
    static final String DEFAULT = "app.proxy.default-trusted-pattern";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String fallback = environment.getProperty(DEFAULT);
        if (fallback != null && environment.containsProperty(VARIABLE) && !StringUtils.hasText(environment.getProperty(VARIABLE))) {
            environment.getPropertySources().addFirst(new MapPropertySource("trustedProxyDefault", Map.of(VARIABLE, fallback)));
        }
    }
}
