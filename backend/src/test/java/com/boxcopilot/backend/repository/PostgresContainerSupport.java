package com.boxcopilot.backend.repository;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Points a Spring test context at a shared Postgres running the real Flyway schema.
 *
 * <p>Use it with {@code @ContextConfiguration(initializers = PostgresContainerSupport.class)} and
 * {@code @Testcontainers(disabledWithoutDocker = true)}. The container is a JVM-wide singleton started on first
 * use (test classes skipped because Docker is missing never start it) and stopped by Testcontainers' Ryuk when
 * the JVM exits. The image matches docker-compose.yml. Flyway runs the production migrations and Hibernate does
 * not touch the schema (ddl-auto=none, as in prod).
 */
public class PostgresContainerSupport implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        synchronized (POSTGRES) {
            if (!POSTGRES.isRunning()) {
                POSTGRES.start();
            }
        }
        TestPropertyValues.of(
                "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "spring.datasource.username=" + POSTGRES.getUsername(),
                "spring.datasource.password=" + POSTGRES.getPassword(),
                "spring.datasource.driver-class-name=org.postgresql.Driver",
                "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
                "spring.jpa.hibernate.ddl-auto=none",
                "spring.flyway.enabled=true",
                "spring.flyway.locations=classpath:db/migration"
        ).applyTo(context);
    }
}
