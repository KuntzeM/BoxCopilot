package com.boxcopilot.backend.controller;

import com.boxcopilot.backend.dto.BoxPreviewDTO;
import com.boxcopilot.backend.service.BoxService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Unauthenticated read-only box view; an unknown UUID answers 404 via the global exception handler. */
@RestController
@RequestMapping("/api/v1/public")
public class PublicPreviewController {

    private final BoxService boxService;

    public PublicPreviewController(BoxService boxService) {
        this.boxService = boxService;
    }

    @GetMapping("/{uuid}")
    public BoxPreviewDTO preview(@PathVariable String uuid) {
        return boxService.getPublicPreview(uuid);
    }
}
