package com.boxcopilot.backend.config;

import com.boxcopilot.backend.domain.AuthProvider;
import com.boxcopilot.backend.domain.Role;
import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.dto.CreateMagicLinkRequestDTO;
import com.boxcopilot.backend.mcp.authserver.McpSessionUserResolver;
import com.boxcopilot.backend.repository.UserRepository;
import com.boxcopilot.backend.service.CustomUserDetailsService.CustomUserPrincipal;
import com.boxcopilot.backend.service.MagicLoginTokenService;
import com.boxcopilot.backend.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An admin setting a user's password ends every older session of that user. Real chain over a real socket; each
 * {@link Browser} is one cookie jar. OIDC users have no local password ({@code setPassword} rejects them), so the
 * check does not apply to them and their sessions are untouched.
 *
 * <p>The session's staleness is decided by comparing the user's {@code sessionsValidAfter} marker with the copy the
 * session's principal took at login, not by the session's creation time - see {@link User#passwordResetSince}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:pwreset;DB_CLOSE_DELAY=-1",
        "app.image.storage.path=target/web-runtime-images"})
@ActiveProfiles("dev")
@Import(PasswordResetEndsSessionsTest.OidcSupport.class)
class PasswordResetEndsSessionsTest {

    private static final String PASSWORD = "right-password-1";
    private static final String NEW_PASSWORD = "brand-new-password-2";
    private static final String PROTECTED = "/api/v1/boxes";

    @LocalServerPort
    int port;
    @Autowired
    UserRepository userRepository;
    @Autowired
    UserService userService;
    @Autowired
    MagicLoginTokenService magicLoginTokenService;
    @Autowired
    PasswordEncoder passwordEncoder;

    private final class Browser {
        final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

        HttpResponse<String> send(HttpRequest.Builder b) throws Exception {
            return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpRequest.Builder req(String path) {
            return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        }

        String csrf() throws Exception {
            String body = send(req("/api/v1/csrf").GET()).body();
            Matcher m = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
            assertThat(m.find()).isTrue();
            return m.group(1);
        }

        int login(String user, String password) throws Exception {
            return send(req("/api/auth/login").header("X-XSRF-TOKEN", csrf())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("username=" + user + "&password=" + password)))
                    .statusCode();
        }

        int magicLogin(String token) throws Exception {
            return send(req("/api/v1/auth/magic-login").header("X-XSRF-TOKEN", csrf())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"token\":\"" + token + "\"}"))).statusCode();
        }

        int resetPasswordOf(long userId, String password) throws Exception {
            return send(req("/api/v1/admin/users/" + userId + "/password").header("X-XSRF-TOKEN", csrf())
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString("{\"password\":\"" + password + "\"}"))).statusCode();
        }

        int status() throws Exception {
            return send(req(PROTECTED).GET()).statusCode();
        }
    }

    private User newUser(Role role) {
        User u = new User("u-" + UUID.randomUUID().toString().substring(0, 8), "U", AuthProvider.LOCAL, role);
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return userRepository.save(u);
    }

    private Browser loggedInAs(User u) throws Exception {
        Browser b = new Browser();
        assertThat(b.login(u.getUsername(), PASSWORD)).isEqualTo(200);
        assertThat(b.status()).isEqualTo(200);
        return b;
    }

    private String freshMagicToken(User u) {
        return magicLoginTokenService.generateMagicLink(u.getId(), new CreateMagicLinkRequestDTO(1), "test")
                .getToken();
    }

    @Test
    void formLoginSessionAndMagicLinkSessionEndAtTheNextRequestAfterTheReset() throws Exception {
        User admin = newUser(Role.ADMIN);
        User victim = newUser(Role.USER);
        Browser form = loggedInAs(victim);
        Browser magic = new Browser();
        assertThat(magic.magicLogin(freshMagicToken(victim))).isEqualTo(200);
        assertThat(magic.status()).isEqualTo(200);

        assertThat(loggedInAs(admin).resetPasswordOf(victim.getId(), NEW_PASSWORD)).isEqualTo(200);

        assertThat(form.status()).isEqualTo(401);
        assertThat(magic.status()).isEqualTo(401);
    }

    @Test
    void outstandingMagicLoginTokensStopWorking() throws Exception {
        User admin = newUser(Role.ADMIN);
        User victim = newUser(Role.USER);
        String token = freshMagicToken(victim);

        assertThat(loggedInAs(admin).resetPasswordOf(victim.getId(), NEW_PASSWORD)).isEqualTo(200);

        assertThat(new Browser().magicLogin(token)).isEqualTo(401);
    }

    /** (a) the anonymous session a browser got from the CSRF fetch predates the reset; logging in on it must work. */
    @Test
    void loginAfterTheResetSurvivesAnAnonymousSessionThatPredatesTheReset() throws Exception {
        User admin = newUser(Role.ADMIN);
        User victim = newUser(Role.USER);
        Browser browser = new Browser();
        browser.csrf(); // creates the anonymous session before the reset
        Thread.sleep(1100); // creation time and reset are in different seconds

        assertThat(loggedInAs(admin).resetPasswordOf(victim.getId(), NEW_PASSWORD)).isEqualTo(200);

        assertThat(browser.login(victim.getUsername(), NEW_PASSWORD)).isEqualTo(200);
        assertThat(browser.status()).isEqualTo(200);
        assertThat(browser.status()).isEqualTo(200);
    }

    /** (b) */
    @Test
    void adminResettingSomeoneElsesPasswordKeepsTheAdminSession() throws Exception {
        User admin = newUser(Role.ADMIN);
        User victim = newUser(Role.USER);
        Browser adminBrowser = loggedInAs(admin);

        assertThat(adminBrowser.resetPasswordOf(victim.getId(), NEW_PASSWORD)).isEqualTo(200);

        assertThat(adminBrowser.status()).isEqualTo(200);
    }

    /** (c) */
    @Test
    void adminResettingTheOwnPasswordEndsTheOwnSession() throws Exception {
        User admin = newUser(Role.ADMIN);
        Browser adminBrowser = loggedInAs(admin);

        assertThat(adminBrowser.resetPasswordOf(admin.getId(), NEW_PASSWORD)).isEqualTo(200);

        assertThat(adminBrowser.status()).isEqualTo(401);
        assertThat(new Browser().login(admin.getUsername(), NEW_PASSWORD)).isEqualTo(200);
    }

    /**
     * (d) the marker is compared for inequality against what the session snapshotted at login, so there is no
     * clock tolerance to get wrong: a login right after a reset (same millisecond or second) is valid, the next
     * reset ends it, and two resets in a row never produce the same marker.
     */
    @Test
    void aLoginImmediatelyAfterAResetIsValidAndTheNextResetEndsItDeterministically() throws Exception {
        User admin = newUser(Role.ADMIN);
        User victim = newUser(Role.USER);
        Browser adminBrowser = loggedInAs(admin);

        for (int i = 0; i < 3; i++) {
            assertThat(adminBrowser.resetPasswordOf(victim.getId(), NEW_PASSWORD + i)).isEqualTo(200);
            Browser fresh = new Browser();
            assertThat(fresh.login(victim.getUsername(), NEW_PASSWORD + i)).isEqualTo(200);
            assertThat(fresh.status()).isEqualTo(200);
            assertThat(adminBrowser.resetPasswordOf(victim.getId(), NEW_PASSWORD + "x" + i)).isEqualTo(200);
            assertThat(fresh.status()).isEqualTo(401);
        }
        userService.setPassword(victim.getId(), NEW_PASSWORD + "a");
        var first = userRepository.findById(victim.getId()).orElseThrow().getSessionsValidAfter();
        userService.setPassword(victim.getId(), NEW_PASSWORD + "b");
        assertThat(userRepository.findById(victim.getId()).orElseThrow().getSessionsValidAfter()).isAfter(first);
    }

    @Test
    void mcpAuthorizePathRejectsASessionOlderThanTheReset() {
        User victim = newUser(Role.USER);
        CustomUserPrincipal principal = new CustomUserPrincipal(userRepository.findById(victim.getId()).orElseThrow());
        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null,
                principal.getAuthorities());
        McpSessionUserResolver resolver = new McpSessionUserResolver(userRepository);
        assertThat(resolver.resolve(authentication)).isPresent();

        userService.setPassword(victim.getId(), NEW_PASSWORD);

        assertThat(resolver.resolve(authentication)).isEmpty();
        CustomUserPrincipal fresh = new CustomUserPrincipal(userRepository.findById(victim.getId()).orElseThrow());
        assertThat(resolver.resolve(
                UsernamePasswordAuthenticationToken.authenticated(fresh, null, fresh.getAuthorities()))).isPresent();
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
