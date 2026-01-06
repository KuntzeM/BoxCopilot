package com.boxcopilot.backend.service;

import com.boxcopilot.backend.domain.BoxNumberPool;
import com.boxcopilot.backend.repository.BoxNumberPoolRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Service for managing box number assignment and recycling.
 * Ensures thread-safe box number allocation and reuse of deleted numbers.
 */
@Service
public class BoxNumberService {

    private static final Logger log = LoggerFactory.getLogger(BoxNumberService.class);

    private final BoxNumberPoolRepository poolRepository;

    public BoxNumberService(BoxNumberPoolRepository poolRepository) {
        this.poolRepository = poolRepository;
    }

    /**
     * Holt die nächste verfügbare Box-Nummer (kleinste freie Nummer)
     * Thread-safe durch @Transactional
     */
    @Transactional
    public Integer getNextAvailableBoxNumber() {
        return poolRepository
                .findFirstByIsAvailableTrueOrderByBoxNumberAsc()
                .map(pool -> {
                    pool.setIsAvailable(false);
                    pool.setLastUsedAt(Instant.now());
                    poolRepository.save(pool);
                    log.info("Assigned box number {} from pool", pool.getBoxNumber());
                    return pool.getBoxNumber();
                })
                .orElseGet(this::createNewBoxNumber);
    }

    /**
     * Gibt eine Box-Nummer zurück in den Pool
     */
    @Transactional
    public void releaseBoxNumber(Integer boxNumber) {
        if (boxNumber == null) {
            log.warn("Attempted to release null box number");
            return;
        }

        poolRepository.findById(boxNumber).ifPresentOrElse(
                pool -> {
                    pool.setIsAvailable(true);
                    poolRepository.save(pool);
                    log.info("Released box number {} back to pool", boxNumber);
                },
                () -> log.warn("Box number {} not found in pool during release", boxNumber)
        );
    }

    /**
     * Erstellt eine neue Box-Nummer falls Pool leer ist
     */
    private Integer createNewBoxNumber() {
        Integer maxNumber = poolRepository.findMaxBoxNumber().orElse(0);
        Integer newNumber = maxNumber + 1;

        BoxNumberPool pool = new BoxNumberPool();
        pool.setBoxNumber(newNumber);
        pool.setIsAvailable(false);
        pool.setLastUsedAt(Instant.now());
        pool.setCreatedAt(Instant.now());
        poolRepository.save(pool);

        log.info("Created new box number {}", newNumber);
        return newNumber;
    }

    /**
     * Gibt Pool-Statistiken zurück (für Admin-API)
     */
    @Transactional(readOnly = true)
    public PoolStatus getPoolStatus() {
        long totalNumbers = poolRepository.count();
        long availableNumbers = poolRepository.countByIsAvailableTrue();
        Integer maxNumber = poolRepository.findMaxBoxNumber().orElse(0);
        List<Integer> available = poolRepository.findAvailableNumbers();

        return new PoolStatus(
                totalNumbers,
                availableNumbers,
                maxNumber,
                available.isEmpty() ? null : available.get(0),
                available
        );
    }

    /**
     * DTO für Pool-Status
     */
    public record PoolStatus(
            long totalNumbers,
            long availableNumbers,
            Integer highestNumber,
            Integer nextNumber,
            List<Integer> availableNumbersList
    ) {}
}
