package com.boxcopilot.backend.config;

import com.boxcopilot.backend.domain.Box;
import com.boxcopilot.backend.domain.BoxNumberPool;
import com.boxcopilot.backend.repository.BoxNumberPoolRepository;
import com.boxcopilot.backend.repository.BoxRepository;
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
    private final BoxNumberPoolRepository poolRepository;

    public BoxNumberMigration(BoxRepository boxRepository, BoxNumberPoolRepository poolRepository) {
        this.boxRepository = boxRepository;
        this.poolRepository = poolRepository;
    }

    @PostConstruct
    @Transactional
    public void migrateExistingBoxes() {
        // Find all boxes without a box_number
        List<Box> boxesWithoutNumbers = boxRepository.findAll().stream()
                .filter(box -> box.getBoxNumber() == null)
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .toList();

        if (boxesWithoutNumbers.isEmpty()) {
            log.info("No boxes need box number migration");
            return;
        }

        log.info("Starting box number migration for {} boxes", boxesWithoutNumbers.size());

        int newNumber = 1;
        for (Box box : boxesWithoutNumbers) {
            // Create pool entry
            BoxNumberPool pool = new BoxNumberPool();
            pool.setBoxNumber(newNumber);
            pool.setIsAvailable(false);
            pool.setLastUsedAt(Instant.now());
            pool.setCreatedAt(Instant.now());
            poolRepository.save(pool);

            // Assign number to box
            box.setBoxNumber(newNumber);
            boxRepository.save(box);

            log.debug("Assigned box number {} to box ID {}", newNumber, box.getId());
            newNumber++;
        }

        log.info("Box number migration completed. Assigned {} numbers", boxesWithoutNumbers.size());
    }
}
