package com.boxcopilot.backend.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A session written by the Spring Boot 3.5 build (committed capture of a real hash, Redis 7.4.11, throwaway
 * data) must never turn into an HTTP 500 for every request that still carries its cookie. Acceptable: the
 * session loads, or it is treated as unknown (the user signs in again).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:redissessions;DB_CLOSE_DELAY=-1",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.security.oauth2.client.registration.nextcloud.client-id=x",
        "spring.security.oauth2.client.registration.nextcloud.client-secret=x",
        "spring.security.oauth2.client.registration.nextcloud.redirect-uri=http://localhost/login/oauth2/code/nextcloud",
        "spring.security.oauth2.client.registration.nextcloud.provider=offline",
        "spring.security.oauth2.client.provider.offline.authorization-uri=http://localhost/authorize",
        "spring.security.oauth2.client.provider.offline.token-uri=http://localhost/token",
        "spring.security.oauth2.client.provider.offline.user-info-uri=http://localhost/userinfo",
        "spring.security.oauth2.client.provider.offline.jwk-set-uri=http://localhost/jwks",
        "spring.data.redis.password=",
        "frontend.url=http://localhost:3000",
        "nextcloud.logout-url=http://localhost/logout",
        "app.image.storage.path=target/web-runtime-images",
        "app.admin.password=redis-session-test-pw"})
@ActiveProfiles("prod")
class RedisSessionCompatibilityTest {

    private static final String FIXTURE = "baseline-3.5/redis-session-fixture.txt";

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4.11-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @LocalServerPort
    int port;
    @Autowired
    SessionRepository<? extends Session> sessions;
    @Autowired
    RedisConnectionFactory redisConnectionFactory;

    private String sessionId;

    private static byte[] javaSerialized(Object value) {
        try (java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
             java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(bytes)) {
            out.writeObject(value);
            out.flush();
            return bytes.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Loads the 3.5 capture (KEY line, then FIELD name + base64 value per field) into Redis. */
    private void loadThe35Session(boolean renewTimestamps) throws Exception {
        List<String> lines = new String(new org.springframework.core.io.ClassPathResource(FIXTURE).getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines().toList();
        String key = null;
        String field = null;
        Map<byte[], byte[]> hash = new LinkedHashMap<>();
        for (String line : lines) {
            if (line.startsWith("KEY ")) {
                key = line.substring(4).trim();
            } else if (line.startsWith("FIELD ")) {
                field = line.substring(6).trim();
            } else if (line.startsWith("  base64:")) {
                hash.put(field.getBytes(StandardCharsets.UTF_8), Base64.getDecoder().decode(line.substring(9).trim()));
            }
        }
        assertThat(key).startsWith("spring:session:sessions:");
        assertThat(hash).hasSize(4);
        // The capture is old by now and would be dropped as expired before its attributes are ever read. Only the
        // two timestamps are renewed (same JDK serialization the 3.5 build used); the attributes stay the 3.5 bytes.
        long now = System.currentTimeMillis();
        for (String timestamp : renewTimestamps ? List.of("lastAccessedTime", "creationTime") : List.<String>of()) {
            hash.keySet().stream().filter(k -> new String(k, StandardCharsets.UTF_8).equals(timestamp)).findFirst()
                    .ifPresent(k -> hash.put(k, javaSerialized(now)));
        }
        sessionId = key.substring("spring:session:sessions:".length());
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            connection.keyCommands().del(key.getBytes(StandardCharsets.UTF_8));
            connection.hashCommands().hMSet(key.getBytes(StandardCharsets.UTF_8), hash);
            connection.keyCommands().expire(key.getBytes(StandardCharsets.UTF_8), 1800);
        }
    }

    /** The security context (principal, authorities) written by 3.5 deserializes under Spring Security 7. */
    @Test
    void the35SessionLoadsWithItsPrincipal() throws Exception {
        loadThe35Session(true);

        Session session = sessions.findById(sessionId);

        assertThat(session).isNotNull();
        assertThat(session.getAttributeNames()).containsExactly("SPRING_SECURITY_CONTEXT");
        SecurityContext context = session.getAttribute("SPRING_SECURITY_CONTEXT");
        assertThat(context.getAuthentication().getName()).isEqualTo("admin");
        assertThat(context.getAuthentication().isAuthenticated()).isTrue();
        assertThat(context.getAuthentication().getAuthorities()).extracting(Object::toString).contains("ROLE_ADMIN");
    }

    @Test
    void aRequestCarryingThe35CookieIsStillSignedIn() throws Exception {
        loadThe35Session(true);

        RealTomcat.Reply reply = new RealTomcat.Client(port).send("GET", "/api/v1/me", null, null,
                "Cookie", sessionCookie());

        assertThat(reply.status()).as(reply.body()).isEqualTo(200);
        assertThat(reply.body()).contains("\"authenticated\":true").contains("\"username\":\"admin\"")
                .contains("\"role\":\"ADMIN\"").contains("\"csrfToken\"");
    }

    /** The capture as committed has long expired: that is the "user signs in again" outcome, never a 5xx. */
    @Test
    void anExpired35SessionIsAnonymousNotAServerError() throws Exception {
        loadThe35Session(false);

        assertThat(sessions.findById(sessionId)).isNull();
        RealTomcat.Reply protectedApi = new RealTomcat.Client(port).send("GET", "/api/v1/boxes", null, null, "Cookie", sessionCookie());

        assertThat(protectedApi.status()).as(protectedApi.body()).isEqualTo(401);
    }

    /** A stored security context this build cannot deserialize reads as no session attribute: anonymous, never 500. */
    @Test
    void anUndeserializableSecurityContextIsAnonymousNotAServerError() throws Exception {
        loadThe35Session(true);
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            connection.hashCommands().hSet(("spring:session:sessions:" + sessionId).getBytes(StandardCharsets.UTF_8),
                    "sessionAttr:SPRING_SECURITY_CONTEXT".getBytes(StandardCharsets.UTF_8),
                    new byte[]{(byte) 0xAC, (byte) 0xED, 0, 5, 0x73, 0x72, 0, 1, 'X'});
        }

        RealTomcat.Reply me = new RealTomcat.Client(port).send("GET", "/api/v1/me", null, null, "Cookie", sessionCookie());
        RealTomcat.Reply protectedApi = new RealTomcat.Client(port).send("GET", "/api/v1/boxes", null, null, "Cookie", sessionCookie());

        assertThat(me.status()).as(me.body()).isEqualTo(200);
        assertThat(me.body()).contains("\"authenticated\":false");
        assertThat(protectedApi.status()).as(protectedApi.body()).isEqualTo(401);
    }

    private String sessionCookie() {
        return "SESSION=" + Base64.getEncoder().encodeToString(sessionId.getBytes(StandardCharsets.UTF_8));
    }
}
