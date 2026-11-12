package com.boxcopilot.backend.repository;

import com.boxcopilot.backend.domain.Box;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BoxRepository extends JpaRepository<Box, Long> {
    java.util.List<Box> findAllByOrderByBoxNumberDescIdDesc();
    Optional<Box> findByUuid(String uuid);

    @EntityGraph(attributePaths = "items")
    Optional<Box> findByBoxNumber(Integer boxNumber);

    /** Legacy boxes still without a number (startup backfill); unordered, the caller sorts. */
    List<Box> findByBoxNumberIsNull();

    /** Boxes that already carry a number (startup backfill reserves them in the pool). */
    List<Box> findByBoxNumberIsNotNull();

    /** Boxes whose current or target room equals {@code room} ignoring case, in the given order. */
    List<Box> findByCurrentRoomIgnoreCaseOrTargetRoomIgnoreCase(String currentRoom, String targetRoom, Sort sort);
}
