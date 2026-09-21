package com.boxcopilot.backend.repository;

import com.boxcopilot.backend.domain.Box;
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

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@ActiveProfiles("test")
class BoxRepositoryMcpSearchTest {

    private static final Pageable FIRST_50 = PageRequest.of(0, 50, Sort.by("boxNumber"));

    @Autowired
    private BoxRepository boxRepository;

    @Autowired
    private TestEntityManager em;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private static List<Integer> numbers(List<Box> boxes) {
        return boxes.stream().map(Box::getBoxNumber).toList();
    }

    @Test
    void findByBoxNumberLoadsItemsWithASingleQuery() {
        Box box = box(em, 7, "Kitchen", "Living room", "Plates");
        item(em, box, "plate");
        item(em, box, "bowl");
        em.flush();
        em.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        Box found = boxRepository.findByBoxNumber(7).orElseThrow();

        assertThat(found.getItems()).extracting("name").containsExactlyInAnyOrder("plate", "bowl");
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void findByBoxNumberReturnsEmptyForUnknownNumber() {
        assertThat(boxRepository.findByBoxNumber(999)).isEmpty();
    }

    @Test
    void searchMatchesCurrentRoomTargetRoomDescriptionAndExactNumber() {
        box(em, 1, "Kitchen", "Cellar", "crockery");
        box(em, 2, "Bedroom", "Kitchen", "linen");
        box(em, 3, "Bedroom", "Bedroom", "Kitchen towels");
        box(em, 4, "Office", "Office", "books");
        box(em, 12, "Office", "Office", "cables");
        em.flush();
        em.clear();

        assertThat(numbers(boxRepository.searchByTerm("kitchen", FIRST_50))).containsExactly(1, 2, 3);
        assertThat(numbers(boxRepository.searchByTerm("BOOK", FIRST_50))).containsExactly(4);
        assertThat(numbers(boxRepository.searchByTerm("12", FIRST_50))).containsExactly(12);
        assertThat(numbers(boxRepository.searchByTerm("1", FIRST_50))).containsExactly(1);
    }

    @Test
    void searchAppliesPageSizeAsLimit() {
        for (int number = 1; number <= 5; number++) {
            box(em, number, "Kitchen", "Kitchen", null);
        }
        em.flush();
        em.clear();

        assertThat(numbers(boxRepository.searchByTerm("kitchen", PageRequest.of(0, 2, Sort.by("boxNumber")))))
                .containsExactly(1, 2);
    }

    @Test
    void searchTreatsWildcardsLiterally() {
        box(em, 1, "Kitchen", "Kitchen", "100% cotton");
        box(em, 2, "Kitchen", "Kitchen", "a_b");
        box(em, 3, "Kitchen", "Kitchen", "plain");
        box(em, 4, "Kitchen", "Kitchen", "C:\\dir");
        em.flush();
        em.clear();

        assertThat(numbers(boxRepository.searchByTerm("%", FIRST_50))).containsExactly(1);
        assertThat(numbers(boxRepository.searchByTerm("_", FIRST_50))).containsExactly(2);
        assertThat(numbers(boxRepository.searchByTerm("\\", FIRST_50))).containsExactly(4);
    }

    @Test
    void searchInRoomCombinesRoomAndTerm() {
        box(em, 1, "Kitchen", "Cellar", "crockery");
        box(em, 2, "Kitchen", "Cellar", "pans");
        box(em, 3, "Bedroom", "Kitchen", "crockery");
        box(em, 4, "Bedroom", "Bedroom", "crockery");
        em.flush();
        em.clear();

        assertThat(numbers(boxRepository.searchByTermInRoom("crock", "kitchen", FIRST_50))).containsExactly(1, 3);
    }

    @Test
    void searchInRoomTreatsWildcardsLiterally() {
        box(em, 1, "Kitchen", "Kitchen", "100% cotton");
        box(em, 2, "Kitchen", "Kitchen", "plain");
        em.flush();
        em.clear();

        assertThat(numbers(boxRepository.searchByTermInRoom("%", "Kitchen", FIRST_50))).containsExactly(1);
    }

    @Test
    void findByRoomMatchesEitherRoomExactlyIgnoringCase() {
        box(em, 1, "Kitchen", "Cellar", null);
        box(em, 2, "Bedroom", "kitchen", null);
        box(em, 3, "Kitchen garden", "Cellar", null);
        em.flush();
        em.clear();

        assertThat(numbers(boxRepository.findByCurrentRoomIgnoreCaseOrTargetRoomIgnoreCase(
                "KITCHEN", "KITCHEN", FIRST_50))).containsExactly(1, 2);
    }
}
