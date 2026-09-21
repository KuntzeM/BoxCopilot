package com.boxcopilot.backend.repository;

import com.boxcopilot.backend.domain.Box;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BoxRepository extends JpaRepository<Box, Long> {
    java.util.List<Box> findAllByOrderByBoxNumberDescIdDesc();
    Optional<Box> findByUuid(String uuid);

    @EntityGraph(attributePaths = "items")
    Optional<Box> findByBoxNumber(Integer boxNumber);

    /**
     * Boxes whose rooms or description contain the term (case-insensitive) or whose number equals it.
     * LIKE wildcards in the term are escaped by Spring Data and matched literally.
     */
    @Query("""
            select b from Box b
            where lower(b.currentRoom) like lower(concat('%', :#{escape(#term)}, '%')) escape :#{escapeCharacter()}
               or lower(b.targetRoom) like lower(concat('%', :#{escape(#term)}, '%')) escape :#{escapeCharacter()}
               or lower(cast(b.description as string)) like lower(concat('%', :#{escape(#term)}, '%')) escape :#{escapeCharacter()}
               or str(b.boxNumber) = :term
            """)
    List<Box> searchByTerm(@Param("term") String term, Pageable pageable);

    /** Like {@link #searchByTerm} but restricted to boxes whose current or target room equals {@code room}. */
    @Query("""
            select b from Box b
            where (lower(b.currentRoom) = lower(:room) or lower(b.targetRoom) = lower(:room))
              and (lower(b.currentRoom) like lower(concat('%', :#{escape(#term)}, '%')) escape :#{escapeCharacter()}
                or lower(b.targetRoom) like lower(concat('%', :#{escape(#term)}, '%')) escape :#{escapeCharacter()}
                or lower(cast(b.description as string)) like lower(concat('%', :#{escape(#term)}, '%')) escape :#{escapeCharacter()}
                or str(b.boxNumber) = :term)
            """)
    List<Box> searchByTermInRoom(@Param("term") String term, @Param("room") String room, Pageable pageable);

    List<Box> findByCurrentRoomIgnoreCaseOrTargetRoomIgnoreCase(String currentRoom, String targetRoom,
                                                                Pageable pageable);
}
