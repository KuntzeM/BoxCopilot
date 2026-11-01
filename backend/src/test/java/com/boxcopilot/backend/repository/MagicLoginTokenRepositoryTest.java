package com.boxcopilot.backend.repository;

import com.boxcopilot.backend.domain.AuthProvider;
import com.boxcopilot.backend.domain.MagicLoginToken;
import com.boxcopilot.backend.domain.Role;
import com.boxcopilot.backend.domain.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.flyway.enabled=false")
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MagicLoginTokenRepositoryTest {

    private static final int CONCURRENT_REDEEMERS = 8;

    @Autowired
    private MagicLoginTokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("magic-user", "Magic User", AuthProvider.LOCAL, Role.USER));
    }

    @AfterEach
    void tearDown() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void consumeMarksValidTokenAsUsedAndReportsOneRow() {
        tokenRepository.save(new MagicLoginToken("valid-token", user, 24));

        int rows = tokenRepository.consume("valid-token", LocalDateTime.now());

        assertThat(rows).isEqualTo(1);
        assertThat(tokenRepository.findByToken("valid-token")).get().extracting(MagicLoginToken::getUsed).isEqualTo(true);
    }

    @Test
    void consumeRejectsAnAlreadyUsedToken() {
        tokenRepository.save(new MagicLoginToken("used-token", user, 24));
        tokenRepository.consume("used-token", LocalDateTime.now());

        assertThat(tokenRepository.consume("used-token", LocalDateTime.now())).isZero();
    }

    @Test
    void consumeRejectsAnExpiredToken() {
        MagicLoginToken expired = new MagicLoginToken("expired-token", user, 24);
        expired.setExpiryDate(LocalDateTime.now().minusMinutes(1));
        tokenRepository.save(expired);

        assertThat(tokenRepository.consume("expired-token", LocalDateTime.now())).isZero();
        assertThat(tokenRepository.findByToken("expired-token")).get().extracting(MagicLoginToken::getUsed).isEqualTo(false);
    }

    @Test
    void consumeRejectsAnUnknownToken() {
        assertThat(tokenRepository.consume("unknown-token", LocalDateTime.now())).isZero();
    }

    @Test
    void parallelRedemptionsOfOneTokenSucceedExactlyOnce() throws Exception {
        tokenRepository.save(new MagicLoginToken("race-token", user, 24));
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REDEEMERS);
        CountDownLatch startSignal = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < CONCURRENT_REDEEMERS; i++) {
                Callable<Integer> redeem = () -> {
                    startSignal.await();
                    return tokenRepository.consume("race-token", LocalDateTime.now());
                };
                results.add(executor.submit(redeem));
            }
            startSignal.countDown();

            int successfulRedemptions = 0;
            for (Future<Integer> result : results) {
                successfulRedemptions += result.get(30, TimeUnit.SECONDS);
            }

            assertThat(successfulRedemptions).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }
}
