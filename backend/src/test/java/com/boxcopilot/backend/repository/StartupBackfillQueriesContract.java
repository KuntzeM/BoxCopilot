package com.boxcopilot.backend.repository;

import com.boxcopilot.backend.domain.Box;
import com.boxcopilot.backend.domain.Item;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.boxcopilot.backend.repository.McpReadRepositoryTestSupport.box;
import static com.boxcopilot.backend.repository.McpReadRepositoryTestSupport.item;
import static org.assertj.core.api.Assertions.assertThat;

/** Guard queries of the startup backfills; shared by the H2 and Postgres runs. */
@Transactional
abstract class StartupBackfillQueriesContract {

    @Autowired
    protected ItemRepository itemRepository;

    @Autowired
    protected BoxRepository boxRepository;

    @Autowired
    protected TestEntityManager em;

    private Item itemWithToken(Box box, String name, String token) {
        Item item = item(em, box, name);
        item.setImageToken(token);
        return item;
    }

    @Test
    void findsNullEmptyAndBlankTokensOnly() {
        Box box = box(em, 1, "Kitchen", "Kitchen", null);
        itemWithToken(box, "null", null);
        itemWithToken(box, "empty", "");
        itemWithToken(box, "blank", "   ");
        itemWithToken(box, "set", UUID.randomUUID().toString());
        em.flush();
        em.clear();

        assertThat(itemRepository.findWithoutImageToken()).extracting(Item::getName)
                .containsExactlyInAnyOrder("null", "empty", "blank");
    }

    @Test
    void healthyItemsYieldNothing() {
        Box box = box(em, 1, "Kitchen", "Kitchen", null);
        itemWithToken(box, "a", UUID.randomUUID().toString());
        itemWithToken(box, "b", UUID.randomUUID().toString());
        em.flush();
        em.clear();

        assertThat(itemRepository.findWithoutImageToken()).isEmpty();
    }

    @Test
    void splitsBoxesByNumber() {
        box(em, 1, "Kitchen", "Kitchen", null);
        box(em, 2, "Kitchen", "Kitchen", null);
        Box legacy = em.persist(new Box(UUID.randomUUID().toString(), "Cellar", "Cellar", null));
        em.flush();
        em.clear();

        assertThat(boxRepository.findByBoxNumberIsNull()).extracting(Box::getId).containsExactly(legacy.getId());
        assertThat(boxRepository.findByBoxNumberIsNotNull()).extracting(Box::getBoxNumber)
                .containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void emptyTablesYieldNothing() {
        assertThat(itemRepository.findWithoutImageToken()).isEmpty();
        assertThat(boxRepository.findByBoxNumberIsNull()).isEmpty();
        assertThat(boxRepository.findByBoxNumberIsNotNull()).isEmpty();
    }
}
