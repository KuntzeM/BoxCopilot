package com.boxcopilot.backend.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Beans the non-MCP security code needs; the MCP configuration has its own {@code Clock} fallback. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LoginThrottleProperties.class)
class SecurityBeansConfiguration {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock securityClock() {
        return Clock.systemUTC();
    }

    @Bean
    LoginThrottle loginThrottle(Clock clock, LoginThrottleProperties properties) {
        return new LoginThrottle(clock, properties);
    }
}
