package com.boxcopilot.backend.repository;

import com.boxcopilot.backend.domain.Box;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BoxRepository extends JpaRepository<Box, Long> {
    java.util.List<Box> findAllByOrderByBoxNumberDescIdDesc();
    Optional<Box> findByUuid(String uuid);
}
