package com.boxcopilot.backend.controller;

import com.boxcopilot.backend.service.BoxNumberService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for box number pool management.
 * Provides admin endpoints to monitor the box number pool status.
 */
@RestController
@RequestMapping("/api/v1/box-numbers")
public class BoxNumberController {

    private final BoxNumberService boxNumberService;

    public BoxNumberController(BoxNumberService boxNumberService) {
        this.boxNumberService = boxNumberService;
    }

    /**
     * Get the current status of the box number pool.
     * Requires ADMIN role.
     * 
     * @return PoolStatus containing statistics about available and used numbers
     */
    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BoxNumberService.PoolStatus> getPoolStatus() {
        return ResponseEntity.ok(boxNumberService.getPoolStatus());
    }
}
