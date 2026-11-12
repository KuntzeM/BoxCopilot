package com.boxcopilot.backend.config;

import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.repository.MagicLoginTokenRepository;
import com.boxcopilot.backend.repository.UserRepository;
import com.boxcopilot.backend.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Proves that the admin password guard runs before the default admin account is seeded.
 * UserService is registered first on purpose, so only the explicit dependency orders the beans.
 */
class AdminSeedingOrderTest {

    private final UserRepository userRepository = mock(UserRepository.class);

    private ApplicationContextRunner runner(String profile, String adminPassword) {
        return new ApplicationContextRunner()
                .withBean(UserRepository.class, () -> userRepository)
                .withBean(MagicLoginTokenRepository.class, () -> mock(MagicLoginTokenRepository.class))
                .withBean(PasswordEncoder.class, () -> new BCryptPasswordEncoder(4))
                .withUserConfiguration(UserService.class, AdminPasswordGuard.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(profile))
                .withPropertyValues("app.admin.password=" + adminPassword);
    }

    @Test
    void failingGuardPreventsSeedingTheDefaultAdmin() {
        runner("prod", "admin").run(context -> {
            assertThat(context).hasFailed();
            verify(userRepository, never()).save(any(User.class));
        });
    }

    @Test
    void passingGuardAllowsSeedingTheDefaultAdmin() {
        runner("prod", "s3cret-Passw0rd-xyz").run(context -> {
            assertThat(context).hasNotFailed();
            verify(userRepository).save(any(User.class));
        });
    }
}
