package com.boxcopilot.backend.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HSTS_ENABLED=true: a request that Tomcat sees as secure (X-Forwarded-Proto https from the trusted local peer,
 * proxy settings from application-prod.yml) carries exactly the configured HSTS header on the
 * SPA, the API and an early 401.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:hstsenabled;DB_CLOSE_DELAY=-1",
        "app.image.storage.path=target/web-runtime-images",
        "spring.web.resources.static-locations=classpath:/spa-fixture/",
        "app.security.headers.hsts-enabled=true"})
@ActiveProfiles("dev")
@ContextConfiguration(initializers = RealTomcat.DefaultPattern.class)
@Import(RealTomcat.Support.class)
class HstsEnabledTest {

    @LocalServerPort
    int port;

    @Test
    void secureRequestsCarryTheConfiguredHsts() throws Exception {
        for (String path : new String[] {"/", "/app/boxes", "/api/v1/csrf", "/api/v1/boxes"}) {
            RealTomcat.Reply r = new RealTomcat.Client(port).send("GET", path, null, null,
                    "X-Forwarded-Proto", "https", "Accept", "text/html");

            assertThat(RealTomcat.Support.LAST.get().secure()).as(path).isTrue();
            assertThat(r.headers().allValues("Strict-Transport-Security")).as(path)
                    .containsExactly("max-age=31536000; includeSubDomains");
        }
    }
}
