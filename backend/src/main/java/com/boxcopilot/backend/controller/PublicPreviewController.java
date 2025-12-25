package com.boxcopilot.backend.controller;

import com.boxcopilot.backend.domain.Box;
import com.boxcopilot.backend.domain.Item;
import com.boxcopilot.backend.dto.BoxPreviewDTO;
import com.boxcopilot.backend.repository.BoxRepository;
import com.boxcopilot.backend.repository.ItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/public")
public class PublicPreviewController {

    private static final Logger log = LoggerFactory.getLogger(PublicPreviewController.class);

    private final BoxRepository boxRepo;
    private final ItemRepository itemRepo;

    public PublicPreviewController(BoxRepository boxRepo, ItemRepository itemRepo) {
        this.boxRepo = boxRepo;
        this.itemRepo = itemRepo;
    }

    @GetMapping("/{uuid}")
    @Transactional(readOnly = true)
    public ResponseEntity<BoxPreviewDTO> preview(@PathVariable String uuid) {
        log.debug("Public preview requested for box UUID: {}", uuid);
        // Find box directly by UUID
        Optional<Box> boxOpt = boxRepo.findByUuid(uuid);
        if (boxOpt.isEmpty()) {
            log.warn("Public preview failed - box not found: {}", uuid);
            return ResponseEntity.notFound().build();
        }
        
        final Box box = boxOpt.get();

        // Fetch items by box ID (more efficient than loading all items)
        List<Item> items = itemRepo.findByBoxId(box.getId());

        List<BoxPreviewDTO.ItemDTO> itemDTOs = items.stream()
            .map(i -> new BoxPreviewDTO.ItemDTO(i.getName()))
            .collect(Collectors.toList());

        BoxPreviewDTO dto = new BoxPreviewDTO(
            box.getId(),
            box.getUuid(),
            box.getCurrentRoom(),
            box.getTargetRoom(),
            box.getDescription(),
            itemDTOs
        );
        
        dto.setIsFragile(box.getIsFragile());
        dto.setNoStack(box.getNoStack());

        log.info("Public preview generated for box: {} with {} items", uuid, itemDTOs.size());
        return ResponseEntity.ok(dto);
    }
}
