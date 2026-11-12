package com.boxcopilot.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.data.redis.RedisSessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real application under the real {@code prod} profile: {@code spring.autoconfigure.exclude} and the session
 * settings come from application-prod.yml, so the full auto-configuration ordering decides which session store wins.
 * Only what needs an external service is replaced (H2 instead of Postgres, a dummy OIDC provider without discovery,
 * Redis that is never connected to because nothing touches a session here). Sessions silently landing in the
 * database instead of Redis in production is exactly what this guards.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:prodsessions;DB_CLOSE_DELAY=-1",
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
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379",
        "spring.data.redis.password=",
        "frontend.url=http://localhost:3000",
        "nextcloud.logout-url=http://localhost/logout",
        "app.admin.password=prod-session-test-password"})
@ActiveProfiles("prod")
class ProdSessionStoreTest {
    // ponytail: with the exclusion removed the bean assertions still pass today (Redis wins the ordering), so
    // prodExcludesTheJdbcSessionStore is the test that goes red; the bean test guards the outcome if ordering changes.

    @Autowired
    private ApplicationContext context;
    @Autowired
    private Environment environment;

    @Test
    void prodExcludesTheJdbcSessionStore() {
        assertThat(Binder.get(environment).bind("spring.autoconfigure.exclude", String[].class).orElse(new String[0]))
                .contains("org.springframework.boot.session.jdbc.autoconfigure.JdbcSessionAutoConfiguration");
    }

    @Test
    void prodSessionsLiveInRedisNotInTheDatabase() {
        assertThat(context.getBeansOfType(SessionRepository.class).values())
                .hasSize(1).first().isInstanceOf(RedisSessionRepository.class);
        assertThat(context.getBeansOfType(JdbcIndexedSessionRepository.class)).isEmpty();
    }

    /**
     * Documents pre-existing behaviour: sessions expire 30 minutes after their last access (the Spring Session
     * default); {@code spring.session.timeout} in application-prod.yml has no effect with {@code @EnableRedisHttpSession}.
     */
    @Test
    void prodSessionsExpireAfterThirtyMinutesOfInactivity() {
        assertThat(ReflectionTestUtils.getField(context.getBean(RedisSessionRepository.class),
                "defaultMaxInactiveInterval")).isEqualTo(Duration.ofMinutes(30));
    }

    /** The SPA is cross-site behind the proxy: the session cookie Spring Session writes must stay None + Secure. */
    @Test
    void prodSessionCookieIsCrossSiteSecureAndHttpOnly() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        context.getBean(CookieSerializer.class)
                .writeCookieValue(new CookieSerializer.CookieValue(request, response, "session-id"));

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains("SameSite=None").contains("Secure").contains("HttpOnly");
    }
}
