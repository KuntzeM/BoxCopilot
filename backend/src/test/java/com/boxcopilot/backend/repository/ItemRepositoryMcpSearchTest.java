package com.boxcopilot.backend.repository;

import com.boxcopilot.backend.domain.Box;
import com.boxcopilot.backend.domain.Item;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static com.boxcopilot.backend.repository.McpReadRepositoryTestSupport.box;
import static com.boxcopilot.backend.repository.McpReadRepositoryTestSupport.item;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@ActiveProfiles("test")
class ItemRepositoryMcpSearchTest {

    private static final Pageable FIRST_50_BY_NAME = PageRequest.of(0, 50, Sort.by("name"));

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private TestEntityManager em;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private List<String> names(String term) {
        return itemRepository.findByNameContainingIgnoreCase(term, FIRST_50_BY_NAME)
                .stream().map(Item::getName).toList();
    }

    @Test
    void findsByNameCaseInsensitivelyAndSorted() {
        Box box = box(em, 1, "Kitchen", "Kitchen", null);
        item(em, box, "Winter Jacket");
        item(em, box, "jacket hanger");
        item(em, box, "Plates");
        em.flush();
        em.clear();

        assertThat(names("JACKET")).containsExactly("Winter Jacket", "jacket hanger");
    }

    @Test
    void appliesPageSizeAsLimit() {
        Box box = box(em, 1, "Kitchen", "Kitchen", null);
        for (int i = 0; i < 5; i++) {
            item(em, box, "cup " + i);
        }
        em.flush();
        em.clear();

        assertThat(itemRepository.findByNameContainingIgnoreCase("cup", PageRequest.of(0, 3, Sort.by("name"))))
                .hasSize(3);
    }

    @Test
    void percentSignIsTreatedLiterally() {
        Box box = box(em, 1, "Kitchen", "Kitchen", null);
        item(em, box, "100% cotton");
        item(em, box, "socks");
        item(em, box, "shirt");
        em.flush();
        em.clear();

        assertThat(names("%")).containsExactly("100% cotton");
    }

    @Test
    void underscoreIsTreatedLiterally() {
        Box box = box(em, 1, "Kitchen", "Kitchen", null);
        item(em, box, "a_b");
        item(em, box, "axb");
        em.flush();
        em.clear();

        assertThat(names("_")).containsExactly("a_b");
    }

    @Test
    void backslashIsTreatedLiterally() {
        Box box = box(em, 1, "Kitchen", "Kitchen", null);
        item(em, box, "C:\\backup");
        item(em, box, "backup");
        em.flush();
        em.clear();

        assertThat(names("\\")).containsExactly("C:\\backup");
    }

    @Test
    void loadsBoxesWithoutAdditionalQueries() {
        for (int number = 1; number <= 3; number++) {
            item(em, box(em, number, "Room " + number, "Target " + number, null), "lamp " + number);
        }
        em.flush();
        em.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        List<Integer> numbers = itemRepository.findByNameContainingIgnoreCase("lamp", FIRST_50_BY_NAME)
                .stream().map(i -> i.getBox().getBoxNumber()).toList();

        assertThat(numbers).containsExactly(1, 2, 3);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void countsItemsPerBox() {
        Box first = box(em, 1, "Kitchen", "Kitchen", null);
        Box second = box(em, 2, "Kitchen", "Kitchen", null);
        box(em, 3, "Kitchen", "Kitchen", null);
        item(em, first, "a");
        item(em, first, "b");
        item(em, second, "c");
        em.flush();
        em.clear();

        List<BoxItemCount> counts = itemRepository.countItemsByBoxIds(List.of(first.getId(), second.getId()));

        assertThat(counts).extracting(BoxItemCount::getBoxId, BoxItemCount::getItemCount)
                .containsExactlyInAnyOrder(tuple(first.getId(), 2L), tuple(second.getId(), 1L));
    }
}
