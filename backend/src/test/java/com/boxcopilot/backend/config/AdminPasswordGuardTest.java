package com.boxcopilot.backend.config;

import com.boxcopilot.backend.domain.AuthProvider;
import com.boxcopilot.backend.domain.Role;
import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminPasswordGuardTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    @Test
    void prodRejectsMissingConfiguredPassword() {
        AdminPasswordGuard guard = guard("prod", "");

        assertThatThrownBy(guard::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_PASSWORD");
    }

    @Test
    void prodRejectsBlankConfiguredPassword() {
        AdminPasswordGuard guard = guard("prod", "   ");

        assertThatThrownBy(guard::afterPropertiesSet).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void prodRejectsDefaultConfiguredPassword() {
        AdminPasswordGuard guard = guard("prod", "admin");

        assertThatThrownBy(guard::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_PASSWORD");
    }

    @Test
    void prodAcceptsStrongConfiguredPassword() {
        AdminPasswordGuard guard = guard("prod", "s3cret-Passw0rd-xyz");

        assertThatCode(guard::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void devToleratesDefaultConfiguredPassword() {
        AdminPasswordGuard guard = guard("dev", "admin");

        assertThatCode(guard::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void prodRejectsExistingAdminWhoseHashMatchesDefaultPassword() {
        when(userRepository.findByRole(Role.ADMIN))
                .thenReturn(List.of(adminWithPassword("admin")));
        AdminPasswordGuard guard = guard("prod", "s3cret-Passw0rd-xyz");

        assertThatThrownBy(() -> guard.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("default password");
    }

    @Test
    void prodAcceptsExistingAdminWithChangedPassword() {
        when(userRepository.findByRole(Role.ADMIN))
                .thenReturn(List.of(adminWithPassword("s3cret-Passw0rd-xyz")));
        AdminPasswordGuard guard = guard("prod", "s3cret-Passw0rd-xyz");

        assertThatCode(() -> guard.run(null)).doesNotThrowAnyException();
    }

    @Test
    void prodSkipsAdminsWithoutPasswordHash() {
        User oidcAdmin = new User("nc-admin", "NC Admin", AuthProvider.NEXTCLOUD, Role.ADMIN);
        when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(oidcAdmin));
        AdminPasswordGuard guard = guard("prod", "s3cret-Passw0rd-xyz");

        assertThatCode(() -> guard.run(null)).doesNotThrowAnyException();
    }

    @Test
    void devOnlyWarnsWhenAdminHashMatchesDefaultPassword() {
        when(userRepository.findByRole(Role.ADMIN))
                .thenReturn(List.of(adminWithPassword("admin")));
        AdminPasswordGuard guard = guard("dev", "admin");

        assertThatCode(() -> guard.run(null)).doesNotThrowAnyException();
    }

    private AdminPasswordGuard guard(String profile, String configuredPassword) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return new AdminPasswordGuard(environment, userRepository, passwordEncoder, configuredPassword);
    }

    private User adminWithPassword(String rawPassword) {
        User admin = new User("admin", "Administrator", AuthProvider.LOCAL, Role.ADMIN);
        admin.setPasswordHash(passwordEncoder.encode(rawPassword));
        return admin;
    }
}
