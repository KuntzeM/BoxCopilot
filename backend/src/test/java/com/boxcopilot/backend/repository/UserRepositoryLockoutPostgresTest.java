package com.boxcopilot.backend.repository;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs the lockout contract against Postgres 15 with the real Flyway schema; skipped without Docker. */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresContainerSupport.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserRepositoryLockoutPostgresTest extends UserRepositoryLockoutContract {
}
