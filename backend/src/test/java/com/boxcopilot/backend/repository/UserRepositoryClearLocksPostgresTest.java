package com.boxcopilot.backend.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V15 on real Postgres: migrates to V14, plants locked and counted users, then applies V15 on top. Owns its
 * container so the "before" state is exactly the V14 schema.
 */
@Testcontainers(disabledWithoutDocker = true)
class UserRepositoryClearLocksPostgresTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:15");

    private static JdbcTemplate jdbc;
    private static Flyway flyway;

    @BeforeAll
    static void migrateToV14() {
        POSTGRES.start();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplate(dataSource);
        flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("14").load();
        flyway.migrate();
    }

    @AfterAll
    static void stop() {
        POSTGRES.stop();
    }

    @Test
    void v15ClearsLocksAndCountersAndLeavesOtherUsersAlone() {
        insertUser("locked", 5, LocalDateTime.now().plusHours(1));
        insertUser("lapsed", 1, LocalDateTime.now().minusHours(1));
        insertUser("counted", 3, null);
        insertUser("clean", 0, null);

        Flyway.configure().dataSource(jdbc.getDataSource()).locations("classpath:db/migration").load().migrate();

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE locked_until IS NOT NULL OR failed_login_attempts <> 0",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT enabled FROM users WHERE username = 'locked'", Boolean.class))
                .as("only lock columns are touched").isTrue();
    }

    private static void insertUser(String username, int failedAttempts, LocalDateTime lockedUntil) {
        jdbc.update("""
                INSERT INTO users (username, name, auth_provider, role, enabled, created_at,
                                   failed_login_attempts, locked_until)
                VALUES (?, ?, 'LOCAL', 'USER', TRUE, now(), ?, ?)
                """, username, username, failedAttempts, lockedUntil == null ? null : Timestamp.valueOf(lockedUntil));
    }
}
