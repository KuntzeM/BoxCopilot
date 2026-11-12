package com.boxcopilot.backend.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The error contract of the API over a real socket (real Tomcat, real multipart limits, the whole security chain),
 * compared with the Spring Boot 3.5.16 captures in docs/superpowers/plans/artifacts/baseline-3.5 (file numbers in
 * the test names). The body shape {status, message, timestamp[, fieldErrors]} is what the frontend reads.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:weberrors;DB_CLOSE_DELAY=-1",
        "app.image.storage.path=target/web-runtime-images"})
@ActiveProfiles("dev")
@Import(RealTomcat.Support.class)
class WebRuntimeErrorBodiesTest {

    private static final Path IMAGES = Path.of("target/web-runtime-images");

    @LocalServerPort
    int port;

    private RealTomcat.Client admin;
    private long itemId;

    @BeforeAll
    static void cleanImages() throws Exception {
        Files.createDirectories(IMAGES);
    }

    private RealTomcat.Client loggedIn() throws Exception {
        if (admin == null) {
            admin = new RealTomcat.Client(port);
            admin.login("admin", "admin");
        }
        return admin;
    }

    /** An item that owns a stored image, as in the baseline run. */
    private long itemWithImage() throws Exception {
        RealTomcat.Client c = loggedIn();
        long boxId = RealTomcat.Client.idOf(c.json("POST", "/api/v1/boxes", "{\"currentRoom\":\"Cellar\"}").body());
        itemId = RealTomcat.Client.idOf(c.json("POST", "/api/v1/items",
                "{\"name\":\"Lamp\",\"boxId\":" + boxId + "}").body());
        RealTomcat.Reply ok = c.upload("/api/v1/items/" + itemId + "/image", "file", "a.png", "image/png", png());
        assertThat(ok.status()).as(ok.body()).isEqualTo(200);
        assertThat(IMAGES.resolve(itemId + "_thumb.jpg")).exists();
        return itemId;
    }

    private static byte[] png() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    private static void assertError(RealTomcat.Reply r, int status, String message) {
        assertThat(r.status()).as(r.body()).isEqualTo(status);
        assertThat(r.headers().firstValue("content-type").orElse("")).startsWith("application/json");
        assertThat(r.body()).contains("\"status\":" + status).contains("\"message\":\"" + message + "\"")
                .contains("\"timestamp\"");
    }

    // ---- 3.5 baseline: same status, same body -------------------------------------------------------------

    @Test
    void malformedJson_41() throws Exception {
        assertError(loggedIn().mutate("POST", "/api/v1/boxes", "application/json",
                "{not json".getBytes(StandardCharsets.UTF_8)), 400, "Malformed request body");
    }

    @Test
    void wrongTypeInJson_53() throws Exception {
        assertError(loggedIn().json("POST", "/api/v1/items", "{\"boxId\":\"abc\",\"name\":\"x\"}"),
                400, "Malformed request body");
    }

    @Test
    void validationErrorsKeepTheFieldErrorsMap_52_40() throws Exception {
        RealTomcat.Reply empty = loggedIn().json("POST", "/api/v1/items", "{}");
        assertError(empty, 400, "Validation failed");
        assertThat(empty.body()).contains("\"fieldErrors\":{")
                .contains("\"boxId\":\"Box ID is required\"").contains("\"name\":\"Name is required\"");

        RealTomcat.Reply tooLong = loggedIn().json("POST", "/api/v1/boxes",
                "{\"currentRoom\":\"" + "x".repeat(300) + "\"}");
        assertError(tooLong, 400, "Validation failed");
        assertThat(tooLong.body()).contains("\"currentRoom\":\"Current room must be at most 255 characters\"");
    }

    @Test
    void missingRequestParameter_59() throws Exception {
        assertError(loggedIn().send("GET", "/api/v1/items/search", null, null),
                500, "An unexpected error occurred");
    }

    @Test
    void missingMultipartPart_70() throws Exception {
        long id = itemWithImage();
        assertError(loggedIn().upload("/api/v1/items/" + id + "/image", "other", "a.png", "image/png", png()),
                500, "An unexpected error occurred");
        // rejected before the service ran: the stored image is untouched
        assertThat(IMAGES.resolve(id + "_thumb.jpg")).exists();
        assertThat(IMAGES.resolve(id + "_large.jpg")).exists();
    }

    @Test
    void uploadOfANonImage_68() throws Exception {
        long id = itemWithImage();
        assertError(loggedIn().upload("/api/v1/items/" + id + "/image", "file", "a.txt", "text/plain",
                "hello".getBytes(StandardCharsets.UTF_8)), 400, "File must be an image");
        // an image content type over text bytes is rejected as unreadable
        assertError(loggedIn().upload("/api/v1/items/" + id + "/image", "file", "a.jpg", "image/jpeg",
                "hello".getBytes(StandardCharsets.UTF_8)), 400, "File is not a readable image");
        // the new upload is validated before the old image is replaced: a rejected upload keeps the existing image
        assertThat(IMAGES.resolve(id + "_thumb.jpg")).exists();
        assertThat(IMAGES.resolve(id + "_large.jpg")).exists();
    }

    @Test
    void uploadAbove15Mb_69() throws Exception {
        long id = itemWithImage();
        byte[] big = new byte[16 * 1024 * 1024];
        RealTomcat.Reply r = loggedIn().uploadRaw("/api/v1/items/" + id + "/image", "file", "big.png", "image/png", big);
        // Tomcat answers while the client is still sending, so a raw socket reads the reply reliably
        assertThat(r.status()).isEqualTo(413);
        assertThat(r.body()).contains("\"status\":413").contains("\"message\":\"Upload too large\"");
        // the multipart parser refuses the request before the service runs: stored image untouched
        assertThat(IMAGES.resolve(id + "_thumb.jpg")).exists();
        assertThat(IMAGES.resolve(id + "_large.jpg")).exists();
    }

    @Test
    void uploadJustBelowTheLimitIsAccepted() throws Exception {
        long id = itemWithImage();
        byte[] almost = new byte[14 * 1024 * 1024];
        // not an image, so the service refuses it - but the multipart layer let it through
        assertError(loggedIn().upload("/api/v1/items/" + id + "/image", "file", "x.txt", "text/plain", almost),
                400, "File must be an image");
    }

    // ---- not in the baseline: observed on 4.1, pinned so a later change shows up --------------------------

    @Test
    void unsupportedMethod() throws Exception {
        long id = itemWithImage();
        assertError(loggedIn().send("GET", "/api/v1/items/" + id + "/image", null, null),
                405, "Method not allowed");
    }

    @Test
    void unknownPath() throws Exception {
        assertError(loggedIn().send("GET", "/api/v1/does-not-exist", null, null), 404, "Not found");
    }

    @Test
    void wrongContentType() throws Exception {
        assertError(loggedIn().mutate("POST", "/api/v1/boxes", "text/plain",
                "{\"currentRoom\":\"x\"}".getBytes(StandardCharsets.UTF_8)), 500, "An unexpected error occurred");
    }

    @Test
    void wrongPathVariableType() throws Exception {
        assertError(loggedIn().mutate("DELETE", "/api/v1/items/abc/image", null, null), 400,
                "Invalid value for parameter 'id'");
    }
}
