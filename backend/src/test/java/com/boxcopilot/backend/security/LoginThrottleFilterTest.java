package com.boxcopilot.backend.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.boxcopilot.backend.mcp.MutableTestClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LoginThrottleFilterTest {

    private final LoginThrottle throttle = new LoginThrottle(new MutableTestClock(),
            new LoginThrottleProperties(10, 5, Duration.ofMinutes(15)));
    private final LoginThrottleFilter filter = new LoginThrottleFilter(throttle);
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(LoginThrottleFilter.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    private static MockHttpServletRequest post(String path, String remoteAddr, String username) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(remoteAddr);
        if (username != null) {
            request.setParameter("username", username);
        }
        return request;
    }

    private record Outcome(MockHttpServletResponse response, MockFilterChain chain) {
        boolean passedOn() {
            return chain.getRequest() != null;
        }
    }

    private Outcome run(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return new Outcome(response, chain);
    }

    private void fail(String ip, int times) {
        for (int i = 0; i < times; i++) {
            throttle.recordFailure(ip, "user" + i);
        }
    }

    @Test
    void openCallerPassesThrough() throws Exception {
        assertThat(run(post("/api/auth/login", "10.1.1.1", "alice")).passedOn()).isTrue();
    }

    @Test
    void exhaustedIpGets429WithRetryAfterAndJsonAndTheChainIsNeverCalled() throws Exception {
        fail("10.1.1.1", 10);

        Outcome outcome = run(post("/api/auth/login", "10.1.1.1", "alice"));

        assertThat(outcome.passedOn()).isFalse();
        assertThat(outcome.response().getStatus()).isEqualTo(429);
        assertThat(outcome.response().getHeader("Retry-After")).isEqualTo("900");
        assertThat(outcome.response().getContentType()).startsWith("application/json");
        assertThat(outcome.response().getContentAsString())
                .isEqualTo("{\"error\":\"Too many failed login attempts\",\"retryAfterSeconds\":900}");
    }

    @Test
    void anotherAddressIsNotAffected() throws Exception {
        fail("10.1.1.1", 10);

        assertThat(run(post("/api/auth/login", "10.1.1.2", "alice")).passedOn()).isTrue();
    }

    @Test
    void ipAndUserLimitBlocksOnlyThatUsernameFromThatIp() throws Exception {
        for (int i = 0; i < 5; i++) {
            throttle.recordFailure("10.1.1.1", "alice");
        }

        assertThat(run(post("/api/auth/login", "10.1.1.1", "ALICE ")).response().getStatus()).isEqualTo(429);
        assertThat(run(post("/api/auth/login", "10.1.1.1", "bob")).passedOn()).isTrue();
        assertThat(run(post("/api/auth/login", "10.1.1.2", "alice")).passedOn()).isTrue();
    }

    @Test
    void magicLoginIsGuardedByTheIpQuotaOnly() throws Exception {
        for (int i = 0; i < 5; i++) {
            throttle.recordFailure("10.1.1.1", "alice");
        }
        // a username parameter on the magic-login request is not used for the key
        assertThat(run(post("/api/v1/auth/magic-login", "10.1.1.1", "alice")).passedOn()).isTrue();

        fail("10.1.1.1", 10);
        Outcome blocked = run(post("/api/v1/auth/magic-login", "10.1.1.1", null));
        assertThat(blocked.response().getStatus()).isEqualTo(429);
        assertThat(blocked.passedOn()).isFalse();
    }

    @Test
    void otherRequestsAreNeverThrottled() throws Exception {
        fail("10.1.1.1", 10);

        MockHttpServletRequest getLogin = new MockHttpServletRequest("GET", "/api/auth/login");
        getLogin.setRemoteAddr("10.1.1.1");
        assertThat(run(getLogin).passedOn()).isTrue();
        assertThat(run(post("/api/v1/boxes", "10.1.1.1", null)).passedOn()).isTrue();
        MockHttpServletRequest getMagic = new MockHttpServletRequest("GET", "/api/v1/auth/magic-login");
        getMagic.setRemoteAddr("10.1.1.1");
        assertThat(run(getMagic).passedOn()).isTrue();
    }

    @Test
    void ipv6AddressesOfOneSlash64ShareTheBucket() throws Exception {
        for (int i = 0; i < 10; i++) {
            LoginThrottleFilter.recordFailure(throttle, post("/api/auth/login", "2001:db8:1:2::1", null), "u" + i);
        }

        assertThat(run(post("/api/auth/login", "2001:db8:1:2:aaaa::9", "alice")).response().getStatus())
                .isEqualTo(429);
        assertThat(run(post("/api/auth/login", "2001:db8:1:3::1", "alice")).passedOn()).isTrue();
    }

    /** The client address is getRemoteAddr(); a header sent by the caller is never read here (Tomcat decides). */
    @Test
    void aForwardedForHeaderOnTheRequestIsIgnored() throws Exception {
        fail("10.1.1.1", 10);
        MockHttpServletRequest spoofed = post("/api/auth/login", "10.1.1.1", "alice");
        spoofed.addHeader("X-Forwarded-For", "198.51.100.77");
        assertThat(run(spoofed).response().getStatus()).isEqualTo(429);

        MockHttpServletRequest other = post("/api/auth/login", "10.1.1.3", "alice");
        other.addHeader("X-Forwarded-For", "10.1.1.1");
        assertThat(run(other).passedOn()).isTrue();
    }

    @Test
    void passingRequestsNeverChangeTheCounters() throws Exception {
        fail("10.1.1.1", 9);
        for (int i = 0; i < 20; i++) {
            assertThat(run(post("/api/auth/login", "10.1.1.1", "alice")).passedOn()).isTrue();
        }
        assertThat(throttle.isBlocked("10.1.1.1", "alice")).isFalse();
    }

    @Test
    void logsOneWarnWhenTheBlockStartsAndNothingPerBlockedRequest() throws Exception {
        for (int i = 0; i < 9; i++) {
            LoginThrottleFilter.recordFailure(throttle, post("/api/auth/login", "10.1.1.1", null), "u" + i);
        }
        assertThat(warns()).isZero();

        LoginThrottleFilter.recordFailure(throttle, post("/api/auth/login", "10.1.1.1", null), "u9");
        for (int i = 0; i < 5; i++) {
            run(post("/api/auth/login", "10.1.1.1", "alice"));
        }

        assertThat(warns()).isEqualTo(1);
        assertThat(appender.list).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
    }

    private long warns() {
        return appender.list.stream().filter(e -> e.getLevel() == Level.WARN).count();
    }
}
