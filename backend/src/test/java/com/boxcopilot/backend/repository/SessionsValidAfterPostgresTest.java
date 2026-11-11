package com.boxcopilot.backend.repository;

import com.boxcopilot.backend.domain.AuthProvider;
import com.boxcopilot.backend.domain.Role;
import com.boxcopilot.backend.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** V16's column on real Postgres: nullable by default, and the UTC marker round-trips through the entity. */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresContainerSupport.class)
class SessionsValidAfterPostgresTest {

    @Autowired
    UserRepository userRepository;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void columnIsNullByDefaultAndTheMarkerRoundTrips() {
        User user = userRepository.saveAndFlush(new User("sva-user", "Sva", AuthProvider.LOCAL, Role.USER));
        assertThat(jdbc.queryForObject("SELECT sessions_valid_after FROM users WHERE id = ?", Timestamp.class,
                user.getId())).isNull();

        LocalDateTime marker = LocalDateTime.of(2026, 11, 11, 10, 15, 30, 123_456_000);
        user.setSessionsValidAfter(marker);
        userRepository.saveAndFlush(user);

        assertThat(jdbc.queryForObject("SELECT sessions_valid_after FROM users WHERE id = ?", Timestamp.class,
                user.getId()).toLocalDateTime()).isEqualTo(marker);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getSessionsValidAfter()).isEqualTo(marker);
    }
}
