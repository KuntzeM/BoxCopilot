package com.boxcopilot.backend.controller;

import com.boxcopilot.backend.service.ItemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/items")
public class PublicItemImageController {

    private static final Logger log = LoggerFactory.getLogger(PublicItemImageController.class);

    private final ItemService itemService;

    public PublicItemImageController(ItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping("/{token}/image")
    public ResponseEntity<Resource> getImageByToken(@PathVariable String token,
                                                    @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        log.debug("Retrieving thumbnail for image token: {}", token);
        Resource image = itemService.getImageByToken(token);
        long lastModified = itemService.getImageLastModifiedByToken(token);
        String eTag = "\"" + token + "-" + lastModified + "\"";

        if (ifNoneMatch != null && ifNoneMatch.equals(eTag)) {
            return ResponseEntity.status(304).build();
        }

        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_JPEG)
            .header(HttpHeaders.CACHE_CONTROL, "max-age=3600, must-revalidate")
            .header(HttpHeaders.ETAG, eTag)
            .header(HttpHeaders.LAST_MODIFIED, String.valueOf(lastModified))
            .body(image);
    }

    @GetMapping("/{token}/image/large")
    public ResponseEntity<Resource> getLargeImageByToken(@PathVariable String token,
                                                         @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        log.debug("Retrieving large image for token: {}", token);
        Resource image = itemService.getLargeImageByToken(token);
        long lastModified = itemService.getLargeImageLastModifiedByToken(token);
        String eTag = "\"" + token + "-large-" + lastModified + "\"";

        if (ifNoneMatch != null && ifNoneMatch.equals(eTag)) {
            return ResponseEntity.status(304).build();
        }

        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_JPEG)
            .header(HttpHeaders.CACHE_CONTROL, "max-age=3600, must-revalidate")
            .header(HttpHeaders.ETAG, eTag)
            .header(HttpHeaders.LAST_MODIFIED, String.valueOf(lastModified))
            .body(image);
    }
}
