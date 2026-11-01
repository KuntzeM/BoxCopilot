package com.boxcopilot.backend.repository;

import com.boxcopilot.backend.domain.Box;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.boxcopilot.backend.repository.McpReadRepositoryTestSupport.box;
import static com.boxcopilot.backend.repository.McpReadRepositoryTestSupport.item;
import static org.assertj.core.api.Assertions.assertThat;

/** Test bodies shared by the H2 and Postgres runs; transactional here because Spring resolves it on the declaring class. */
@Transactional
abstract class BoxRepositoryMcpContract {

    private static final Sort BOX_NUMBER_ORDER = Sort.by("boxNumber");

    @Autowired
    protected BoxRepository boxRepository;

    @Autowired
    protected TestEntityManager em;

    @Autowired
    protected EntityManagerFactory entityManagerFactory;

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
    void longDescriptionRoundTripsWithoutTruncation() {
        String description = "x".repeat(600) + " Needle";
        box(em, 1, "Kitchen", "Kitchen", description);
        em.flush();
        em.clear();

        assertThat(boxRepository.findByBoxNumber(1).orElseThrow().getDescription()).isEqualTo(description);
    }

    @Test
    void sortsByNumericBoxNumber() {
        box(em, 30, "Kitchen", "Kitchen", null);
        box(em, 4, "Kitchen", "Kitchen", null);
        box(em, 200, "Kitchen", "Kitchen", null);
        em.flush();
        em.clear();

        assertThat(numbers(boxRepository.findAll(BOX_NUMBER_ORDER))).containsExactly(4, 30, 200);
    }

    @Test
    void findByRoomMatchesEitherRoomExactlyIgnoringCase() {
        box(em, 30, "Kitchen", "Cellar", null);
        box(em, 4, "Bedroom", "kitchen", null);
        box(em, 5, "Kitchen garden", "Cellar", null);
        em.flush();
        em.clear();

        assertThat(numbers(boxRepository.findByCurrentRoomIgnoreCaseOrTargetRoomIgnoreCase(
                "KITCHEN", "KITCHEN", BOX_NUMBER_ORDER))).containsExactly(4, 30);
    }
}
