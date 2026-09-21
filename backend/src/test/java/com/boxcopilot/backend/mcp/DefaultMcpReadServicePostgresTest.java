package com.boxcopilot.backend.mcp;

import com.boxcopilot.backend.repository.PostgresContainerSupport;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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
