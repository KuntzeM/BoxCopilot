package com.boxcopilot.backend.mcp;

import com.boxcopilot.backend.repository.PostgresContainerSupport;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs the shared service contract against Postgres 15 with the real Flyway schema; skipped without Docker. */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresContainerSupport.class)
@Import(DefaultMcpReadServiceContract.ServiceConfig.class)
class DefaultMcpReadServicePostgresTest extends DefaultMcpReadServiceContract {
}
