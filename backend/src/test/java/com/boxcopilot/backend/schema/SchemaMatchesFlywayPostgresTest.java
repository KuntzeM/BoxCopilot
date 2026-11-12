package com.boxcopilot.backend.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.boxcopilot.backend.repository.PostgresContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The entities must match the schema the Flyway migrations build on an empty Postgres 15, exactly as production
 * gets it. Hibernate's {@code ddl-auto=validate} fails the context start on any missing table or column and on a
 * column type that the entity mapping cannot read (it does not compare indexes or constraints). The initializer
 * of {@link PostgresContainerSupport} runs V1..V14 and sets ddl-auto to none; the validate mode is applied after it.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(initializers = {PostgresContainerSupport.class, SchemaMatchesFlywayPostgresTest.Validate.class})
class SchemaMatchesFlywayPostgresTest {

    static class Validate implements org.springframework.context.ApplicationContextInitializer<
            org.springframework.context.ConfigurableApplicationContext> {
        @Override
        public void initialize(org.springframework.context.ConfigurableApplicationContext context) {
            org.springframework.boot.test.util.TestPropertyValues.of("spring.jpa.hibernate.ddl-auto=validate")
                    .applyTo(context);
        }
    }

    @Autowired
    Environment env;

    @Test
    void entitiesValidateAgainstTheFlywaySchema() {
        // the context started with validate (see class comment); this pins that validate was really in force
        assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }
}
