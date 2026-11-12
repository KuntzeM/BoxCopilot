package com.boxcopilot.backend.config;

import com.boxcopilot.backend.domain.Box;
import com.boxcopilot.backend.domain.BoxNumberPool;
import com.boxcopilot.backend.repository.BoxNumberPoolRepository;
import com.boxcopilot.backend.repository.BoxRepository;
import com.boxcopilot.backend.service.BoxNumberService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Pins what the startup box-number backfill does for legacy data (boxes without number, missing pool rows). */
@DataJpaTest(properties = "spring.flyway.enabled=false")
@ActiveProfiles("test")
@Import(BoxNumberService.class)
class BoxNumberMigrationTest {

    @Autowired BoxRepository boxRepository;
    @Autowired BoxNumberPoolRepository poolRepository;
    @Autowired BoxNumberService boxNumberService;
    @Autowired PlatformTransactionManager txManager;
    @Autowired EntityManager em;

    private Box box(Integer number, Instant createdAt) {
        Box box = new Box(UUID.randomUUID().toString(), "Room", "Room", null);
        box.setBoxNumber(number);
        box.setCreatedAt(createdAt);
        return boxRepository.save(box);
    }

    private void pool(int number, boolean available) {
        BoxNumberPool p = new BoxNumberPool();
        p.setBoxNumber(number);
        p.setIsAvailable(available);
        p.setCreatedAt(Instant.now());
        poolRepository.save(p);
    }

    private void migrate() {
        em.flush();
        em.clear();
        new BoxNumberMigration(boxRepository, boxNumberService, new TransactionTemplate(txManager), poolRepository)
                .migrateExistingBoxes();
        em.flush();
        em.clear();
    }

    private Integer numberOf(Box box) {
        return boxRepository.findById(box.getId()).orElseThrow().getBoxNumber();
    }

    private List<Integer> reservedPool() {
        return poolRepository.findAll().stream().filter(p -> !p.getIsAvailable())
                .map(BoxNumberPool::getBoxNumber).sorted().toList();
    }

    @Test
    void legacyBoxesWithoutNumberGetNumbersInCreationOrder() {
        Instant now = Instant.now();
        Box newer = box(null, now);
        Box older = box(null, now.minusSeconds(60));
        Box undated = box(null, null);

        migrate();

        assertThat(numberOf(older)).isEqualTo(1);
        assertThat(numberOf(newer)).isEqualTo(2);
        assertThat(numberOf(undated)).isEqualTo(3);
        assertThat(reservedPool()).containsExactly(1, 2, 3);
    }

    @Test
    void existingNumbersStayAndAreReservedEvenWithoutPoolRow() {
        Box a = box(5, Instant.now());
        Box b = box(2, Instant.now());
        Box legacy = box(null, Instant.now());

        migrate();

        assertThat(numberOf(a)).isEqualTo(5);
        assertThat(numberOf(b)).isEqualTo(2);
        assertThat(numberOf(legacy)).isEqualTo(6);
        assertThat(reservedPool()).containsExactly(2, 5, 6);
    }

    @Test
    void releasedPoolNumberIsReusedForLegacyBox() {
        Box keep = box(2, Instant.now());
        pool(1, true);
        Box legacy = box(null, Instant.now());

        migrate();

        assertThat(numberOf(keep)).isEqualTo(2);
        assertThat(numberOf(legacy)).isEqualTo(1);
        assertThat(reservedPool()).containsExactly(1, 2);
    }

    @Test
    void healthyDatabaseWithPoolIsLeftUntouched() {
        Box a = box(1, Instant.now());
        Box b = box(2, Instant.now());
        pool(1, false);
        pool(2, false);
        pool(3, true);

        migrate();

        assertThat(numberOf(a)).isEqualTo(1);
        assertThat(numberOf(b)).isEqualTo(2);
        assertThat(poolRepository.count()).isEqualTo(3);
        assertThat(reservedPool()).containsExactly(1, 2);
    }

    @Test
    void numberedBoxesWithEmptyPoolGetPoolRows() {
        box(4, Instant.now());

        migrate();

        assertThat(reservedPool()).containsExactly(4);
    }

    @Test
    void emptyDatabaseIsANoOp() {
        migrate();

        assertThat(poolRepository.count()).isZero();
    }
}
