package com.boxcopilot.backend.repository;

import com.boxcopilot.backend.domain.BoxNumberPool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for managing the box number pool.
 */
@Repository
public interface BoxNumberPoolRepository extends JpaRepository<BoxNumberPool, Integer> {

    /**
     * Findet die kleinste verfügbare Box-Nummer
     */
    Optional<BoxNumberPool> findFirstByIsAvailableTrueOrderByBoxNumberAsc();

    /**
     * Findet die höchste vergebene Box-Nummer
     */
    @Query("SELECT MAX(b.boxNumber) FROM BoxNumberPool b")
    Optional<Integer> findMaxBoxNumber();

    /**
     * Gibt alle verfügbaren Nummern zurück (für Admin-API)
     */
    @Query("SELECT b.boxNumber FROM BoxNumberPool b WHERE b.isAvailable = true ORDER BY b.boxNumber ASC")
    List<Integer> findAvailableNumbers();

    /**
     * Zählt verfügbare Nummern
     */
    long countByIsAvailableTrue();
}
