package com.boxcopilot.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TrustedProxyDefaultTest {

    private String resolved(Map<String, Object> operator) {
        MockEnvironment env = new MockEnvironment().withProperty(TrustedProxyDefault.DEFAULT, "DEFAULT");
        env.getPropertySources().addFirst(new MapPropertySource("operator", operator));
        new TrustedProxyDefault().postProcessEnvironment(env, null);
        return env.getProperty("TRUSTED_PROXY_PATTERN", "UNSET");
    }

    @Test
    void blankBecomesTheDefault() {
        assertThat(resolved(Map.of("TRUSTED_PROXY_PATTERN", ""))).isEqualTo("DEFAULT");
        assertThat(resolved(Map.of("TRUSTED_PROXY_PATTERN", "  "))).isEqualTo("DEFAULT");
    }

    @Test
    void customAndUnsetAreLeftAlone() {
        assertThat(resolved(Map.of("TRUSTED_PROXY_PATTERN", "172.18.0.2"))).isEqualTo("172.18.0.2");
        assertThat(resolved(Map.of())).isEqualTo("UNSET");
    }
}
