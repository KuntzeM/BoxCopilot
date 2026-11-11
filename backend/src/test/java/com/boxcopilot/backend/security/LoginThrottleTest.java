package com.boxcopilot.backend.security;

import com.boxcopilot.backend.mcp.MutableTestClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LoginThrottleTest {

    private final MutableTestClock clock = new MutableTestClock();
    private final LoginThrottle throttle =
            new LoginThrottle(clock, new LoginThrottleProperties(10, 5, Duration.ofMinutes(15)));

    @Test
    void ipUserLimitBlocksThatPairOnlyAfterFiveFailures() {
        for (int i = 0; i < 4; i++) {
            throttle.recordFailure("1.1.1.1", "alice");
        }
        assertThat(throttle.isBlocked("1.1.1.1", "alice")).isFalse();
        throttle.recordFailure("1.1.1.1", "alice");
        assertThat(throttle.isBlocked("1.1.1.1", "alice")).isTrue();
        assertThat(throttle.isBlocked("1.1.1.1", "bob")).isFalse();
    }

    @Test
    void anotherClientIsNeverAffectedSoNobodyCanLockSomeoneElsesAccount() {
        for (int i = 0; i < 50; i++) {
            throttle.recordFailure("6.6.6.6", "alice");
        }
        assertThat(throttle.isBlocked("6.6.6.6", "alice")).isTrue();
        assertThat(throttle.isBlocked("2.2.2.2", "alice")).isFalse();
    }

    @Test
    void ipLimitBlocksEveryUsernameAfterTenFailures() {
        for (int i = 0; i < 10; i++) {
            throttle.recordFailure("1.1.1.1", "user" + i);
        }
        assertThat(throttle.isBlocked("1.1.1.1", "fresh")).isTrue();
        assertThat(throttle.isBlocked("1.1.1.1", null)).isTrue();
    }

    @Test
    void nullUsernameCountsAgainstTheIpOnly() {
        for (int i = 0; i < 10; i++) {
            throttle.recordFailure("1.1.1.1", null);
        }
        assertThat(throttle.isBlocked("1.1.1.1", null)).isTrue();
    }

    @Test
    void usernameIsNormalizedAndTruncated() {
        for (int i = 0; i < 5; i++) {
            throttle.recordFailure("1.1.1.1", i % 2 == 0 ? " Alice " : "ALICE");
        }
        assertThat(throttle.isBlocked("1.1.1.1", "alice")).isTrue();

        String longName = "x".repeat(200);
        for (int i = 0; i < 5; i++) {
            throttle.recordFailure("3.3.3.3", longName + i);
        }
        assertThat(throttle.isBlocked("3.3.3.3", longName + "different")).isTrue();
    }

    @Test
    void ipv6AddressesInTheSame64ShareAKey() {
        String a = ClientAddress.normalize("2001:db8:1:2::1");
        String b = ClientAddress.normalize("2001:db8:1:2:ffff::9");
        for (int i = 0; i < 5; i++) {
            throttle.recordFailure(a, "alice");
        }
        assertThat(throttle.isBlocked(b, "alice")).isTrue();
    }

    @Test
    void isBlockedTakesNoPermitAndTheWindowReopens() {
        for (int i = 0; i < 100; i++) {
            assertThat(throttle.isBlocked("1.1.1.1", "alice")).isFalse();
        }
        for (int i = 0; i < 5; i++) {
            throttle.recordFailure("1.1.1.1", "alice");
        }
        assertThat(throttle.retryAfterSeconds()).isEqualTo(900);
        clock.advance(Duration.ofMinutes(15));
        assertThat(throttle.isBlocked("1.1.1.1", "alice")).isFalse();
    }
}
