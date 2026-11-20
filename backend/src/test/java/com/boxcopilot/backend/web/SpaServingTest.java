package com.boxcopilot.backend.web;

import com.boxcopilot.backend.config.SecurityHeadersFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The SPA shipped inside the backend: static files, history-API fallback to {@code index.html}, cache headers and
 * the security headers that nginx used to set - over a real socket with the real application chain (dev profile).
 * The SPA itself is a fixture under {@code src/test/resources/spa-fixture}; the real build lands in
 * {@code classpath:/static/} only inside the Docker image.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:spaserving;DB_CLOSE_DELAY=-1",
        "app.image.storage.path=target/web-runtime-images",
        "spring.web.resources.static-locations=classpath:/spa-fixture/"})
@ActiveProfiles("dev")
@Import(RealTomcat.Support.class)
class SpaServingTest {

    private static final String INDEX_MARKER = "spa-fixture-index";
    private static final String HTML = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

    @LocalServerPort
    int port;

    private RealTomcat.Reply get(String path, String... headers) throws Exception {
        return new RealTomcat.Client(port).send("GET", path, null, null, headers);
    }

    private static String header(RealTomcat.Reply r, String name) {
        List<String> values = r.headers().allValues(name);
        assertThat(values).as(name + " must be sent exactly once").hasSize(1);
        return values.get(0);
    }

    private static List<String> directives(String cacheControl) {
        return Arrays.stream(cacheControl.split(",")).map(String::trim).toList();
    }

    private static void assertSecurityHeaders(RealTomcat.Reply r) {
        assertThat(header(r, "Content-Security-Policy")).isEqualTo(SecurityHeadersFilter.CONTENT_SECURITY_POLICY);
        assertThat(r.headers().allValues("Content-Security-Policy-Report-Only")).isEmpty();
        assertThat(header(r, "X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(header(r, "X-Frame-Options")).isEqualTo("DENY");
        assertThat(header(r, "Referrer-Policy")).isEqualTo("no-referrer");
        // HSTS_ENABLED defaults to false and the request is plain http
        assertThat(r.headers().allValues("Strict-Transport-Security")).isEmpty();
    }

    @Test
    void rootServesIndexHtmlWithNoCache() throws Exception {
        RealTomcat.Reply r = get("/", "Accept", HTML);

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body()).contains(INDEX_MARKER);
        assertThat(r.headers().firstValue("content-type").orElse("")).startsWith("text/html");
        assertThat(header(r, "Cache-Control")).isEqualTo("no-cache");
        assertSecurityHeaders(r);
    }

    @Test
    void indexHtmlItselfIsNoCache() throws Exception {
        RealTomcat.Reply r = get("/index.html", "Accept", HTML);

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body()).contains(INDEX_MARKER);
        assertThat(header(r, "Cache-Control")).isEqualTo("no-cache");
    }

    @Test
    void clientSideRoutesFallBackToIndexHtml() throws Exception {
        for (String route : new String[] {"/app/boxes", "/app/boxes/42/edit", "/app/admin", "/magic",
                "/public/3f1c2a9e-7b1d-4c55-9a3e-0d2f9b7c1e11", "/some/unknown/route/"}) {
            RealTomcat.Reply r = get(route, "Accept", HTML);

            assertThat(r.status()).as(route).isEqualTo(200);
            assertThat(r.body()).as(route).contains(INDEX_MARKER);
            assertThat(header(r, "Cache-Control")).as(route).isEqualTo("no-cache");
            assertSecurityHeaders(r);
        }
    }

    @Test
    void aRouteWithoutAcceptHeaderFallsBackToo() throws Exception {
        RealTomcat.Reply r = get("/app/boxes");

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body()).contains(INDEX_MARKER);
    }

    @Test
    void aRouteRequestedAsJsonIsNotAnsweredWithIndexHtml() throws Exception {
        RealTomcat.Reply r = get("/app/boxes", "Accept", "application/json");

        assertThat(r.status()).isEqualTo(404);
        assertThat(r.body()).doesNotContain(INDEX_MARKER);
    }

    @Test
    void hashedAssetsAreCachedForAYearAndImmutable() throws Exception {
        RealTomcat.Reply r = get("/assets/app-abc123.js");

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body()).contains("spa-fixture-asset");
        assertThat(directives(header(r, "Cache-Control")))
                .containsExactlyInAnyOrder("public", "max-age=31536000", "immutable");
        assertSecurityHeaders(r);
    }

    @Test
    void otherStaticFilesAreServedWithNoCache() throws Exception {
        RealTomcat.Reply r = get("/favicon.svg");

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body()).contains("<svg");
        assertThat(header(r, "Cache-Control")).isEqualTo("no-cache");
    }

    @Test
    void missingFilesWithAnExtensionAre404NotIndexHtml() throws Exception {
        for (String path : new String[] {"/assets/missing-123.js", "/missing.png", "/app/boxes/report.pdf"}) {
            RealTomcat.Reply r = get(path, "Accept", HTML);

            assertThat(r.status()).as(path).isEqualTo(404);
            assertThat(r.body()).as(path).doesNotContain(INDEX_MARKER);
            assertThat(r.headers().allValues("Cache-Control")).as(path)
                    .noneMatch(value -> value.contains("immutable"));
        }
    }

    @Test
    void anAssetPathWithoutExtensionIsNoSpaRoute() throws Exception {
        RealTomcat.Reply r = get("/assets/whatever", "Accept", HTML);

        assertThat(r.status()).isEqualTo(404);
        assertThat(r.body()).doesNotContain(INDEX_MARKER);
    }

    @Test
    void unknownApiPathIs401JsonForAnonymousCallers() throws Exception {
        RealTomcat.Reply r = get("/api/v1/does-not-exist", "Accept", HTML);

        assertThat(r.status()).isEqualTo(401);
        assertThat(r.headers().firstValue("content-type").orElse("")).startsWith("application/json");
        assertThat(r.body()).doesNotContain(INDEX_MARKER);
        assertSecurityHeaders(r);
    }

    @Test
    void unknownApiPathIs404JsonForAuthenticatedCallers() throws Exception {
        RealTomcat.Client admin = new RealTomcat.Client(port);
        admin.login("admin", "admin");

        for (String path : new String[] {"/api/v1/does-not-exist", "/api/nothing/here", "/api"}) {
            RealTomcat.Reply r = admin.send("GET", path, null, null, "Accept", HTML);

            assertThat(r.status()).as(path).isEqualTo(404);
            assertThat(r.headers().firstValue("content-type").orElse("")).as(path).startsWith("application/json");
            assertThat(r.body()).as(path).doesNotContain(INDEX_MARKER);
        }
    }

    @Test
    void apiJsonStillWorksAndCarriesTheSecurityHeaders() throws Exception {
        RealTomcat.Reply r = get("/api/v1/csrf", "Accept", "application/json");

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.headers().firstValue("content-type").orElse("")).startsWith("application/json");
        assertThat(r.body()).contains("\"token\"");
        assertSecurityHeaders(r);
    }

    @Test
    void reservedPrefixesNeverFallBackToIndexHtml() throws Exception {
        for (String path : new String[] {"/mcp", "/mcp/x", "/oauth2/token", "/login/whatever", "/actuator/env",
                "/.well-known/oauth-authorization-server", "/error/x"}) {
            RealTomcat.Reply r = get(path, "Accept", HTML);

            assertThat(r.body()).as(path).doesNotContain(INDEX_MARKER);
            assertThat(r.status()).as(path).isNotEqualTo(200);
        }
    }

    @Test
    void anEncodedReservedPrefixDoesNotBypassAuthentication() throws Exception {
        RealTomcat.Reply r = get("/%61pi/v1/boxes", "Accept", HTML);

        assertThat(r.body()).doesNotContain(INDEX_MARKER);
        // Denied by authorization; the entry point checks the raw URI for "/api/", so it answers with its
        // redirect to the SPA instead of the JSON 401 (behaviour unchanged from before the SPA moved in here).
        assertThat(r.status()).isIn(302, 400, 401, 404);
        if (r.status() == 302) {
            assertThat(r.headers().firstValue("Location")).hasValue("http://localhost:3000");
        }
    }

    @Test
    void healthStaysJson() throws Exception {
        RealTomcat.Reply r = get("/actuator/health", "Accept", HTML);

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body()).contains("\"status\"").doesNotContain(INDEX_MARKER);
    }

    @Test
    void unsafeMethodsOnSpaRoutesStillNeedAuthentication() throws Exception {
        RealTomcat.Reply r = new RealTomcat.Client(port).send("POST", "/app/boxes", null, null, "Accept", HTML);

        assertThat(r.status()).isIn(401, 403);
        assertThat(r.body()).doesNotContain(INDEX_MARKER);
    }
}
