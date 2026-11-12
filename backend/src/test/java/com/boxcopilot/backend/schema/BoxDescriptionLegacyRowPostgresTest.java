package com.boxcopilot.backend.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.boxcopilot.backend.domain.Box;
import com.boxcopilot.backend.repository.BoxRepository;
import com.boxcopilot.backend.repository.PostgresContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Pins how {@code Box.description} ({@code @Lob String}) meets a TEXT column on Postgres. Production rows written by
 * Spring Boot 3.5 / Hibernate 6 hold the object id of a large object in that column (see the baseline dump), so
 * Hibernate 7 has to keep reading and writing that representation: mapping the field as plain text would show the
 * oid as the description of every existing box.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresContainerSupport.class)
class BoxDescriptionLegacyRowPostgresTest {

    // longer than 8 kB (a TOAST-inline limit) and with characters outside ASCII and outside the BMP
    private static final String LONG_UNICODE = ("Zügelkiste: Gläser, Tassen, 日本語, 😀 – ").repeat(400);

    @Autowired
    BoxRepository boxRepository;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void readsAnOidWrittenByHibernate6() {
        // what 3.5 did: a large object holding the UTF-8 text, its oid as the column value
        Long oid = jdbc.queryForObject("select lo_from_bytea(0, convert_to(?, 'UTF8'))", Long.class, LONG_UNICODE);
        jdbc.update("insert into boxes (uuid, box_number, description, is_fragile, no_stack, is_moved_to_target, label_printed)"
                + " values ('legacy-1', 9001, ?, false, false, false, false)", String.valueOf(oid));

        assertThat(boxRepository.findByBoxNumber(9001).orElseThrow().getDescription()).isEqualTo(LONG_UNICODE);
    }

    @Test
    void writesWhatHibernate6Wrote() {
        Box box = new Box("new-1", "Kitchen", "Cellar", LONG_UNICODE);
        box.setBoxNumber(9002);
        boxRepository.saveAndFlush(box);

        String raw = jdbc.queryForObject("select description from boxes where box_number = 9002", String.class);
        assertThat(raw).matches("[0-9]+");
        String stored = jdbc.queryForObject("select convert_from(lo_get(?::oid), 'UTF8')", String.class, Long.valueOf(raw));
        assertThat(stored).isEqualTo(LONG_UNICODE);
    }
}
