package com.boxcopilot.backend.config;

import com.boxcopilot.backend.domain.AuthProvider;
import com.boxcopilot.backend.domain.Role;
import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.test.context.ActiveProfiles;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Login throttling through the real chain over a real socket: the caller's address is resolved by Tomcat's
 * RemoteIpValve exactly as in production (127.0.0.1, the test client, is the trusted proxy; the client address
 * comes from {@code X-Forwarded-For}). Limits are lowered to 3 per IP and 2 per IP+user.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:loginthrottle;DB_CLOSE_DELAY=-1",
        "app.image.storage.path=target/web-runtime-images",
        "server.forward-headers-strategy=native",
        "server.tomcat.remoteip.internal-proxies=127\\.0\\.0\\.1",
        "app.security.login-throttle.ip-limit=3",
        "app.security.login-throttle.ip-user-limit=2"})
@ActiveProfiles("dev")
@Import(LoginThrottleIntegrationTest.OidcSupport.class)
class LoginThrottleIntegrationTest {

    private static final String TOO_MANY = "{\"error\":\"Too many failed login attempts\",\"retryAfterSeconds\":900}";
    private static final String GENERIC = "{\"error\":\"Invalid credentials\"}";
    private static final String FORM = "application/x-www-form-urlencoded";

    @LocalServerPort
    int port;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

    private record Reply(int status, String body, String retryAfter) {
    }

    private void ensureAlice() {
        if (userRepository.findByUsername("alice").isEmpty()) {
            User alice = new User("alice", "Alice", AuthProvider.LOCAL, Role.USER);
            alice.setPasswordHash(passwordEncoder.encode("right-password"));
            userRepository.save(alice);
        }
    }

    private Reply send(String ip, String path, String contentType, String body, boolean withCsrf) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("X-Forwarded-For", ip)
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (withCsrf) {
            b.header("X-XSRF-TOKEN", csrf(ip));
        }
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return new Reply(r.statusCode(), r.body(), r.headers().firstValue("Retry-After").orElse(null));
    }

    private String csrf(String ip) throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/csrf"))
                .header("X-Forwarded-For", ip).GET().build(), HttpResponse.BodyHandlers.ofString());
        Matcher m = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"").matcher(r.body());
        if (!m.find()) {
            throw new IllegalStateException("no csrf token in " + r.body());
        }
        return m.group(1);
    }

    private Reply login(String ip, String user, String password) throws Exception {
        return send(ip, "/api/auth/login", FORM, "username=" + user + "&password=" + password, true);
    }

    private Reply magic(String ip, String token) throws Exception {
        return send(ip, "/api/v1/auth/magic-login", "application/json", "{\"token\":\"" + token + "\"}", true);
    }

    @Test
    void exhaustedIpGetsTheExact429ContractAndAnotherIpStillLogsInToTheSameAccount() throws Exception {
        ensureAlice();
        String a = "203.0.113.1";
        String b = "203.0.113.2";
        for (String name : new String[]{"u1", "u2", "u3"}) {
            assertThat(login(a, name, "wrong")).isEqualTo(new Reply(401, GENERIC, null));
        }

        // even the CORRECT password is turned away from A now - before any lookup or bcrypt
        assertThat(login(a, "alice", "right-password")).isEqualTo(new Reply(429, TOO_MANY, "900"));

        // the account itself was never locked: B logs in with the right password
        assertThat(login(b, "alice", "right-password").status()).isEqualTo(200);
        assertThat(login(a, "alice", "right-password").status()).isEqualTo(429);
    }

    @Test
    void aCorrectLoginNeitherConsumesNorResetsTheBudget() throws Exception {
        ensureAlice();
        String ip = "203.0.113.3";
        assertThat(login(ip, "u1", "wrong").status()).isEqualTo(401);
        assertThat(login(ip, "u2", "wrong").status()).isEqualTo(401);
        // successes between failures are not counted (else the third failure below would already be blocked)
        assertThat(login(ip, "alice", "right-password").status()).isEqualTo(200);
        assertThat(login(ip, "alice", "right-password").status()).isEqualTo(200);
        assertThat(login(ip, "u3", "wrong").status()).isEqualTo(401);
        // ... and they did not reset it either: the third failure used the budget up
        assertThat(login(ip, "alice", "right-password").status()).isEqualTo(429);
    }

    @Test
    void ipAndUserLimitBlocksThatPairOnlyAfterTwoFailures() throws Exception {
        ensureAlice();
        String ip = "203.0.113.4";
        assertThat(login(ip, "alice", "wrong").status()).isEqualTo(401);
        assertThat(login(ip, "alice", "wrong").status()).isEqualTo(401);

        assertThat(login(ip, "alice", "right-password").status()).isEqualTo(429);
        // another username from the same address is not affected (IP budget of 3 has one left)
        assertThat(login(ip, "bob", "wrong").status()).isEqualTo(401);
    }

    @Test
    void magicLoginFloodFromOneIpGets429AndTheLoginFormOfThatIpIsBlockedToo() throws Exception {
        ensureAlice();
        String ip = "203.0.113.5";
        for (int i = 0; i < 3; i++) {
            assertThat(magic(ip, "no-such-token-" + i).status()).isEqualTo(401);
        }
        assertThat(magic(ip, "no-such-token-x")).isEqualTo(new Reply(429, TOO_MANY, "900"));
        assertThat(login(ip, "alice", "right-password").status()).isEqualTo(429);
        assertThat(magic("203.0.113.6", "no-such-token").status()).isEqualTo(401);
    }

    @Test
    void ipv6AddressesOfOneSlash64ShareTheBucket() throws Exception {
        assertThat(login("2001:db8:1:2::1", "u1", "wrong").status()).isEqualTo(401);
        assertThat(login("2001:db8:1:2::2", "u2", "wrong").status()).isEqualTo(401);
        assertThat(login("2001:db8:1:2:ffff::3", "u3", "wrong").status()).isEqualTo(401);

        assertThat(login("2001:db8:1:2::4", "u4", "wrong").status()).isEqualTo(429);
        assertThat(login("2001:db8:1:3::1", "u4", "wrong").status()).isEqualTo(401);
    }

    /** The filter sits behind the CSRF filter: a caller without a token gets the CSRF 403, blocked or not. */
    @Test
    void csrfProtectionOfTheLoginEndpointIsUnchangedForBlockedAndOpenCallers() throws Exception {
        ensureAlice();
        String ip = "203.0.113.7";
        assertThat(send(ip, "/api/auth/login", FORM, "username=alice&password=x", false).status()).isEqualTo(403);
        for (String name : new String[]{"u1", "u2", "u3"}) {
            login(ip, name, "wrong");
        }
        assertThat(send(ip, "/api/auth/login", FORM, "username=alice&password=x", false).status()).isEqualTo(403);
        assertThat(login(ip, "alice", "x").status()).isEqualTo(429);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class OidcSupport {
        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("nextcloud")
                    .clientId("test-client").clientSecret("test-secret")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .redirectUri("http://localhost:8080/login/oauth2/code/{registrationId}")
                    .scope("openid")
                    .authorizationUri("http://localhost/authorize").tokenUri("http://localhost/token")
                    .jwkSetUri("http://localhost/jwks").issuerUri("http://localhost")
                    .userNameAttributeName(IdTokenClaimNames.SUB).build());
        }
    }
}
