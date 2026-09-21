package com.boxcopilot.backend.config;

import com.boxcopilot.backend.domain.Role;
import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Prevents the well-known default admin password from being used.
 * <p>
 * The configured {@code ADMIN_PASSWORD} is validated before the default admin account is seeded
 * (the guard is a dependency of {@code UserService}). Once the application is up, every stored
 * ADMIN password hash is compared against the default password, which covers databases that were
 * created before this check existed. In the {@code prod} profile a violation aborts startup; in
 * other profiles it is only logged, so local development keeps working with the dev default.
 */
@Component("adminPasswordGuard")
public class AdminPasswordGuard implements InitializingBean, ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminPasswordGuard.class);
    private static final String PROD_PROFILE = "prod";
    private static final String DEFAULT_PASSWORD = "admin";

    private final Environment environment;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String configuredPassword;

    public AdminPasswordGuard(Environment environment,
                              UserRepository userRepository,
                              PasswordEncoder passwordEncoder,
                              @Value("${app.admin.password:}") String configuredPassword) {
        this.environment = environment;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.configuredPassword = configuredPassword;
    }

    @Override
    public void afterPropertiesSet() {
        if (configuredPassword == null || configuredPassword.isBlank()) {
            reject("ADMIN_PASSWORD must be set to a non-blank value");
        } else if (DEFAULT_PASSWORD.equals(configuredPassword)) {
            reject("ADMIN_PASSWORD must not be the default password");
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        userRepository.findByRole(Role.ADMIN).stream()
                .filter(this::hasDefaultPassword)
                .forEach(admin -> reject("Admin user '" + admin.getUsername()
                        + "' still has the default password; change it before starting in production"));
    }

    private boolean hasDefaultPassword(User admin) {
        String hash = admin.getPasswordHash();
        return hash != null && passwordEncoder.matches(DEFAULT_PASSWORD, hash);
    }

    private void reject(String message) {
        if (environment.acceptsProfiles(Profiles.of(PROD_PROFILE))) {
            throw new IllegalStateException(message);
        }
        log.warn("{} (tolerated outside the '{}' profile)", message, PROD_PROFILE);
    }
}
