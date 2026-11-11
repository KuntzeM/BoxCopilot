package com.boxcopilot.backend.repository;

import com.boxcopilot.backend.domain.AuthProvider;
import com.boxcopilot.backend.domain.Role;
import com.boxcopilot.backend.domain.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Shared lockout contract; run on H2 and Postgres (the untyped CASE parameter only failed on Postgres). */
abstract class UserRepositoryLockoutContract {

    private static final int PARALLEL_FAILURES = 8;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.save(new User("lock-user", "Lock User", AuthProvider.LOCAL, Role.USER));
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    private int fail() {
        LocalDateTime now = LocalDateTime.now();
        return userRepository.recordFailedLogin("lock-user", now, now.plusHours(1), 5);
    }

    private User reload() {
        return userRepository.findByUsername("lock-user").orElseThrow();
    }

    @Test
    void locksExactlyAtTheThreshold() {
        for (int i = 0; i < 4; i++) {
            fail();
        }
        assertThat(reload().isAccountLocked()).isFalse();

        fail();

        assertThat(reload().getFailedLoginAttempts()).isEqualTo(5);
        assertThat(reload().isAccountLocked()).isTrue();
    }

    @Test
    void failuresDuringAnActiveLockChangeNothing() {
        for (int i = 0; i < 5; i++) {
            fail();
        }
        LocalDateTime lockedUntil = reload().getLockedUntil();

        assertThat(fail()).isZero();

        assertThat(reload().getFailedLoginAttempts()).isEqualTo(5);
        assertThat(reload().getLockedUntil()).isEqualTo(lockedUntil);
    }

    @Test
    void lapsedLockStartsAFreshCount() {
        User user = reload();
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        userRepository.save(user);

        fail();

        assertThat(reload().getFailedLoginAttempts()).isEqualTo(1);
        assertThat(reload().getLockedUntil()).isNull();
    }

    @Test
    void parallelFailuresAreAllCounted() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(PARALLEL_FAILURES);
        CountDownLatch startSignal = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < PARALLEL_FAILURES; i++) {
                results.add(executor.submit(() -> {
                    startSignal.await();
                    return fail();
                }));
            }
            startSignal.countDown();
            for (Future<Integer> result : results) {
                result.get(30, TimeUnit.SECONDS);
            }

            // 8 attempts: 5 count, the rest hit the lock and are ignored; none may be lost before the lock
            assertThat(reload().getFailedLoginAttempts()).isEqualTo(5);
            assertThat(reload().isAccountLocked()).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }
}
