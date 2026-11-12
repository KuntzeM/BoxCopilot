package com.boxcopilot.backend.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real Tomcat, real {@code RemoteIpValve}: {@code forward-headers-strategy} and {@code internal-proxies} come
 * verbatim from application-prod.yml (placeholder default included) and are bound by Boot's own server handling.
 * {@code TRUSTED_PROXY_PATTERN=} (blank) must behave like unset. The client connects from 127.0.0.1, which the default trust pattern covers: the peer is a trusted proxy.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:fwdblank;DB_CLOSE_DELAY=-1",
        "app.image.storage.path=target/web-runtime-images"})
@ActiveProfiles("dev")
@ContextConfiguration(initializers = RealTomcat.BlankPattern.class)
@Import(RealTomcat.Support.class)
class ForwardedHeadersBlankPatternTest {

    @LocalServerPort
    int port;

    @Test
    void forwardedHeadersFromATrustedPeerAreHonoured() throws Exception {
        new RealTomcat.Client(port).send("GET", "/api/v1/csrf", null, null,
                "X-Forwarded-For", "203.0.113.7", "X-Forwarded-Proto", "https");

        assertThat(RealTomcat.Support.LAST.get()).isEqualTo(new RealTomcat.Seen("203.0.113.7", "https", true));
    }

}
