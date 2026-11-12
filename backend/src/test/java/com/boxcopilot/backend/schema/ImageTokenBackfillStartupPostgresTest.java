package com.boxcopilot.backend.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.boxcopilot.backend.mapper.ItemMapper;
import com.boxcopilot.backend.repository.BoxRepository;
import com.boxcopilot.backend.repository.ItemRepository;
import com.boxcopilot.backend.repository.PostgresContainerSupport;
import com.boxcopilot.backend.service.ImageStorageService;
import com.boxcopilot.backend.service.ItemService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Startup image-token backfill on Postgres, run the way the container runs {@code @PostConstruct}: on the raw bean,
 * outside any transaction (no test-managed transaction, no proxy). Committed legacy rows sit next to a box whose
 * {@code @Lob} description Postgres can only read inside a transaction.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresContainerSupport.class)
class ImageTokenBackfillStartupPostgresTest {

    @Autowired ItemRepository itemRepository;
    @Autowired PlatformTransactionManager txManager;
    @Autowired JdbcTemplate jdbc;

    @Test
    void backfillsLegacyItemsOutsideAnyTransaction() {
        jdbc.update("delete from items");
        jdbc.update("delete from boxes");
        Long oid = jdbc.queryForObject("select lo_from_bytea(0, convert_to('Gläser & Tassen', 'UTF8'))", Long.class);
        jdbc.update("insert into boxes (id, uuid, box_number, description, is_fragile, no_stack, is_moved_to_target, label_printed)"
                + " values (7001, 'bf-1', 7001, ?, false, false, false, false)", String.valueOf(oid));
        jdbc.update("insert into items (box_id, name, image_token) values (7001, 'null', null)");
        jdbc.update("insert into items (box_id, name, image_token) values (7001, 'blank', '  ')");
        jdbc.update("insert into items (box_id, name, image_token) values (7001, 'kept', 'keep-me')");

        new ItemService(itemRepository, mock(BoxRepository.class), mock(ItemMapper.class),
                mock(ImageStorageService.class), new TransactionTemplate(txManager)).initImageTokens();

        List<String> tokens = jdbc.queryForList("select image_token from items order by name", String.class);
        assertThat(tokens).hasSize(3).doesNotContainNull().doesNotHaveDuplicates();
        assertThat(jdbc.queryForObject("select image_token from items where name = 'kept'", String.class))
                .isEqualTo("keep-me");
        assertThat(jdbc.queryForObject(
                "select count(*) from items where image_token is null or trim(image_token) = ''", Integer.class))
                .isZero();
        jdbc.update("delete from items");
        jdbc.update("delete from boxes");
    }
}
