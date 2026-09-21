package com.boxcopilot.backend.repository;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs the shared search contract against Postgres 15 with the real Flyway schema; skipped without Docker. */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresContainerSupport.class)
class BoxRepositoryMcpPostgresTest extends BoxRepositoryMcpContract {
}
