package com.boxcopilot.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The prod session cookie: SameSite=None by default (unchanged), switchable through SESSION_COOKIE_SAME_SITE, always Secure. */
class SessionCookieConfigTest {

    private static StandardEnvironment prod(Map<String, Object> operator) throws IOException {
        StandardEnvironment env = new StandardEnvironment();
        for (PropertySource<?> yaml : new YamlPropertySourceLoader()
                .load("application-prod", new ClassPathResource("application-prod.yml"))) {
            env.getPropertySources().addFirst(yaml);
        }
        env.getPropertySources().addFirst(new MapPropertySource("operator", operator));
        return env;
    }

    @Test
    void sameSiteDefaultsToNone() throws IOException {
        assertThat(prod(Map.of()).getProperty("server.servlet.session.cookie.same-site")).isEqualTo("none");
    }

    @Test
    void sameSiteFollowsTheEnvironmentVariable() throws IOException {
        assertThat(prod(Map.of("SESSION_COOKIE_SAME_SITE", "lax"))
                .getProperty("server.servlet.session.cookie.same-site")).isEqualTo("lax");
    }

    @Test
    void cookieStaysSecure() throws IOException {
        assertThat(prod(Map.of("SESSION_COOKIE_SAME_SITE", "lax"))
                .getProperty("server.servlet.session.cookie.secure")).isEqualTo("true");
    }
}
