package com.boxcopilot.backend.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HSTS_ENABLED=false: a request that Tomcat sees as secure (X-Forwarded-Proto https from the trusted local peer,
 * proxy settings from application-prod.yml) carries no HSTS header - Spring Security's default writer is off on the
 * SPA, the API and an early 401.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:hstsdisabled;DB_CLOSE_DELAY=-1",
        "app.image.storage.path=target/web-runtime-images",
        "spring.web.resources.static-locations=classpath:/spa-fixture/",
        "app.security.headers.hsts-enabled=false"})
@ActiveProfiles("dev")
@ContextConfiguration(initializers = RealTomcat.DefaultPattern.class)
@Import(RealTomcat.Support.class)
class HstsDisabledTest {

    @LocalServerPort
    int port;

    @Test
    void secureRequestsCarryNoHsts() throws Exception {
        for (String path : new String[] {"/", "/app/boxes", "/api/v1/csrf", "/api/v1/boxes"}) {
            RealTomcat.Reply r = new RealTomcat.Client(port).send("GET", path, null, null,
                    "X-Forwarded-Proto", "https", "Accept", "text/html");

            assertThat(RealTomcat.Support.LAST.get().secure()).as(path).isTrue();
            assertThat(r.headers().allValues("Strict-Transport-Security")).as(path)
                    .isEmpty();
        }
    }
}
