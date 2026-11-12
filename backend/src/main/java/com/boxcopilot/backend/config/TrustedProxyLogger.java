package com.boxcopilot.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** One startup line so operators can see which trusted-proxy pattern is in effect. */
@Component
@Profile("prod")
class TrustedProxyLogger {

    private static final Logger log = LoggerFactory.getLogger(TrustedProxyLogger.class);

    @Value("${server.tomcat.remoteip.internal-proxies}")
    private String effective;
    @Value("${app.proxy.default-trusted-pattern}")
    private String fallback;

    @EventListener(ApplicationReadyEvent.class)
    void log() {
        log.info("Trusted proxy pattern ({}): {}", effective.equals(fallback) ? "default" : "custom from TRUSTED_PROXY_PATTERN", effective);
    }
}
