package com.boxcopilot.backend.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * gzip as nginx did it, for the SPA's static types only (server.compression in application.yml). JSON is never
 * compressed: API answers carry CSRF tokens and user data, and compressing them would invite BREACH.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:compression;DB_CLOSE_DELAY=-1",
        "app.image.storage.path=target/web-runtime-images",
        "spring.web.resources.static-locations=classpath:/spa-fixture/"})
@ActiveProfiles("dev")
@Import(RealTomcat.Support.class)
class CompressionTest {

    @LocalServerPort
    int port;

    private RealTomcat.Reply get(RealTomcat.Client client, String path) throws Exception {
        return client.send("GET", path, null, null, "Accept-Encoding", "gzip");
    }

    @Test
    void largeJavaScriptAndCssAreGzipped() throws Exception {
        RealTomcat.Client client = new RealTomcat.Client(port);
        for (String path : new String[] {"/assets/big-abc123.js", "/assets/style-abc123.css"}) {
            RealTomcat.Reply r = get(client, path);

            assertThat(r.status()).as(path).isEqualTo(200);
            assertThat(r.headers().firstValue("Content-Encoding")).as(path).hasValue("gzip");
        }
    }

    @Test
    void responsesBelowTheMinimumSizeAreNotCompressed() throws Exception {
        RealTomcat.Reply r = get(new RealTomcat.Client(port), "/assets/app-abc123.js");

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.headers().firstValue("Content-Encoding")).isEmpty();
    }

    @Test
    void largeJsonApiResponsesAreNotCompressed() throws Exception {
        RealTomcat.Client admin = new RealTomcat.Client(port);
        admin.login("admin", "admin");
        for (int i = 0; i < 25; i++) {
            admin.json("POST", "/api/v1/boxes", "{\"currentRoom\":\"Storage room number " + i + "\"}");
        }

        RealTomcat.Reply r = get(admin, "/api/v1/boxes");

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.headers().firstValue("content-type").orElse("")).startsWith("application/json");
        assertThat(r.body().length()).as("big enough to be compressed if JSON were eligible").isGreaterThan(2048);
        assertThat(r.headers().firstValue("Content-Encoding")).isEmpty();
    }
}
