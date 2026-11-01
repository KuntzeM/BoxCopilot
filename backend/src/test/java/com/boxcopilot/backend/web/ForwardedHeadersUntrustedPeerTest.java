package com.boxcopilot.backend.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Same real Tomcat, but the operator narrowed {@code TRUSTED_PROXY_PATTERN} (the real placeholder of
 * application-prod.yml) to a single address, so the 127.0.0.1 test client is a peer outside the trusted set.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:fwduntrusted;DB_CLOSE_DELAY=-1",
        "app.image.storage.path=target/web-runtime-images"})
@ActiveProfiles("dev")
@ContextConfiguration(initializers = RealTomcat.NarrowedPattern.class)
@Import(RealTomcat.Support.class)
class ForwardedHeadersUntrustedPeerTest {

    @LocalServerPort
    int port;
    @Value("${server.tomcat.remoteip.internal-proxies}")
    String pattern;

    @Test
    void theOperatorOverrideReachesTomcatThroughThePlaceholder() {
        assertThat(pattern).isEqualTo("172\\.18\\.0\\.2");
    }

    @Test
    void forwardedHeadersFromAnUntrustedPeerAreIgnored() throws Exception {
        new RealTomcat.Client(port).send("GET", "/api/v1/csrf", null, null,
                "X-Forwarded-For", "203.0.113.7", "X-Forwarded-Proto", "https",
                "X-Forwarded-Host", "evil.example", "X-Forwarded-Port", "8443");

        assertThat(RealTomcat.Support.LAST.get()).isEqualTo(new RealTomcat.Seen("127.0.0.1", "http", false));
    }
}
