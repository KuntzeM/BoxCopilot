package com.boxcopilot.backend.config;

import com.boxcopilot.backend.domain.Box;
import com.boxcopilot.backend.repository.BoxRepository;
import com.boxcopilot.backend.service.BoxNumberService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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

    public BoxNumberMigration(BoxRepository boxRepository, BoxNumberService boxNumberService) {
        this.boxRepository = boxRepository;
        this.boxNumberService = boxNumberService;
    }

    @PostConstruct
    @Transactional
    public void migrateExistingBoxes() {
        // Find all boxes
        List<Box> allBoxes = boxRepository.findAll().stream()
            .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
            .toList();

        if (allBoxes.isEmpty()) {
            log.info("No boxes found for box number migration");
            return;
        }

        // First, ensure existing numbers are reserved in the pool.
        allBoxes.stream()
            .filter(box -> box.getBoxNumber() != null)
            .forEach(box -> boxNumberService.reserveBoxNumber(box.getBoxNumber()));

        // Then assign numbers to boxes that lack one.
        List<Box> boxesWithoutNumbers = allBoxes.stream()
            .filter(box -> box.getBoxNumber() == null)
            .toList();

        if (boxesWithoutNumbers.isEmpty()) {
            log.info("No boxes need box number migration");
            return;
        }

        log.info("Starting box number migration for {} boxes", boxesWithoutNumbers.size());

        for (Box box : boxesWithoutNumbers) {
            Integer next = boxNumberService.getNextAvailableBoxNumber();
            boxNumberService.reserveBoxNumber(next);
            box.setBoxNumber(next);
            boxRepository.save(box);
            log.debug("Assigned box number {} to box ID {}", next, box.getId());
        }

        log.info("Box number migration completed. Assigned {} numbers", boxesWithoutNumbers.size());
    }
}
