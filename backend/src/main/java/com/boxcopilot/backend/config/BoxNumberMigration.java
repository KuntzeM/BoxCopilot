package com.boxcopilot.backend.config;

import com.boxcopilot.backend.domain.Box;
import com.boxcopilot.backend.repository.BoxNumberPoolRepository;
import com.boxcopilot.backend.repository.BoxRepository;
import com.boxcopilot.backend.service.BoxNumberService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Comparator;
import java.util.List;

/**
 * Handles migration of existing boxes to the new box numbering system.
 * Runs once on application startup to assign box numbers to any boxes that don't have one.
 */
@Configuration
public class BoxNumberMigration {

    private static final Logger log = LoggerFactory.getLogger(BoxNumberMigration.class);

    private final BoxRepository boxRepository;
    private final BoxNumberService boxNumberService;
    private final TransactionTemplate transactionTemplate;
    private final BoxNumberPoolRepository poolRepository;

    public BoxNumberMigration(BoxRepository boxRepository, BoxNumberService boxNumberService,
                              TransactionTemplate transactionTemplate, BoxNumberPoolRepository poolRepository) {
        this.boxRepository = boxRepository;
        this.poolRepository = poolRepository;
        this.boxNumberService = boxNumberService;
        this.transactionTemplate = transactionTemplate;
    }

    @PostConstruct
    public void migrateExistingBoxes() {
        transactionTemplate.execute(status -> {
            performMigration();
            return null;
        });
    }

    private void performMigration() {
        List<Box> boxesWithoutNumbers = boxRepository.findByBoxNumberIsNull().stream()
            .sorted(Comparator.comparing(Box::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();

        // Healthy database: every box is numbered and the pool already exists -> nothing to do, no scan.
        if (boxesWithoutNumbers.isEmpty() && poolRepository.count() > 0) {
            log.info("No boxes need box number migration");
            return;
        }

        // First, ensure existing numbers are reserved in the pool.
        boxRepository.findByBoxNumberIsNotNull()
            .forEach(box -> boxNumberService.reserveBoxNumber(box.getBoxNumber()));

        if (boxesWithoutNumbers.isEmpty()) {
            log.info("No boxes need box number migration");
            return;
        }

        log.info("Starting box number migration for {} boxes", boxesWithoutNumbers.size());

        for (Box box : boxesWithoutNumbers) {
            Integer next = boxNumberService.getNextAvailableBoxNumber();
            box.setBoxNumber(next);
            boxRepository.save(box);
            log.debug("Assigned box number {} to box ID {}", next, box.getId());
        }

        log.info("Box number migration completed. Assigned {} numbers", boxesWithoutNumbers.size());
    }
}
