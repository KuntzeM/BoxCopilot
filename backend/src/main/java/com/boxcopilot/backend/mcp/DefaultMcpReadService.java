package com.boxcopilot.backend.mcp;

import com.boxcopilot.backend.domain.Box;
import com.boxcopilot.backend.domain.Item;
import com.boxcopilot.backend.exception.FileStorageException;
import com.boxcopilot.backend.repository.BoxItemCount;
import com.boxcopilot.backend.repository.BoxRepository;
import com.boxcopilot.backend.repository.ItemRepository;
import com.boxcopilot.backend.service.ImageStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
public class DefaultMcpReadService implements McpReadService {

    static final int MIN_LIMIT = 1;
    static final int MAX_LIMIT = 50;
    static final int MAX_QUERY_LENGTH = 100;

    private static final Logger log = LoggerFactory.getLogger(DefaultMcpReadService.class);
    private static final Sort ITEM_SORT = Sort.by("name").and(Sort.by("id"));
    private static final Sort BOX_SORT = Sort.by("boxNumber");

    private final ItemRepository itemRepository;
    private final BoxRepository boxRepository;
    private final ImageStorageService imageStorageService;

    public DefaultMcpReadService(ItemRepository itemRepository, BoxRepository boxRepository,
                                 ImageStorageService imageStorageService) {
        this.itemRepository = itemRepository;
        this.boxRepository = boxRepository;
        this.imageStorageService = imageStorageService;
    }

    @Override
    public List<McpItemHit> searchItems(String query, int limit) {
        String term = requireTerm(query, "query");
        Pageable page = page(limit, ITEM_SORT);
        return itemRepository.findByNameContainingIgnoreCase(term, page).stream()
                .filter(McpDtoMapper::hasBoxNumber)
                .map(McpDtoMapper::toItemHit)
                .toList();
    }

    /**
     * Matches in memory instead of SQL because {@code Box.description} is a {@code @Lob String}: on Postgres
     * Hibernate stores it as a large object and keeps only the object id in the TEXT column, so the column
     * cannot be searched with LIKE. The candidate set is the whole box list (or one room), which is small for a
     * household move; this keeps the search correct whether or not the mapping is fixed later.
     */
    @Override
    public List<McpBoxHit> searchBoxes(String query, String room, int limit) {
        String term = optionalTerm(query, "query");
        String roomName = optionalTerm(room, "room");
        if (term == null && roomName == null) {
            throw new IllegalArgumentException("Either query or room is required");
        }
        checkLimit(limit);
        List<Box> boxes = candidateBoxes(roomName).stream()
                .filter(box -> box.getBoxNumber() != null)
                .filter(box -> term == null || matches(box, term))
                .limit(limit)
                .toList();
        Map<Long, Long> counts = itemCounts(boxes);
        return boxes.stream()
                .map(box -> McpDtoMapper.toBoxHit(box, counts.getOrDefault(box.getId(), 0L)))
                .toList();
    }

    @Override
    public Optional<McpBoxContents> boxContents(int boxNumber) {
        return boxRepository.findByBoxNumber(boxNumber).map(box -> {
            List<Item> items = box.getItems().stream().sorted(McpDtoMapper.ITEM_ORDER).toList();
            return new McpBoxContents(
                    McpDtoMapper.toBoxHit(box, items.size()),
                    McpDtoMapper.description(box),
                    items.stream().map(McpDtoMapper::toItemRef).toList());
        });
    }

    @Override
    public Optional<McpItemImage> itemImage(long itemId, ImageSize size) {
        return itemRepository.findById(itemId)
                .filter(McpDtoMapper::hasImage)
                .filter(McpDtoMapper::hasBoxNumber)
                .flatMap(item -> loadImage(item, size));
    }

    private List<Box> candidateBoxes(String room) {
        if (room == null) {
            return boxRepository.findAll(BOX_SORT);
        }
        return boxRepository.findByCurrentRoomIgnoreCaseOrTargetRoomIgnoreCase(room, room, BOX_SORT);
    }

    private static boolean matches(Box box, String term) {
        return term.equals(String.valueOf(box.getBoxNumber()))
                || containsIgnoreCase(box.getCurrentRoom(), term)
                || containsIgnoreCase(box.getTargetRoom(), term)
                || containsIgnoreCase(box.getDescription(), term);
    }

    private static boolean containsIgnoreCase(String text, String term) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT));
    }

    private Map<Long, Long> itemCounts(List<Box> boxes) {
        if (boxes.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = boxes.stream().map(Box::getId).toList();
        return itemRepository.countItemsByBoxIds(ids).stream()
                .collect(Collectors.toMap(BoxItemCount::getBoxId, BoxItemCount::getItemCount, Long::sum,
                        HashMap::new));
    }

    private Optional<McpItemImage> loadImage(Item item, ImageSize size) {
        Resource resource;
        try {
            resource = size == ImageSize.LARGE
                    ? imageStorageService.getLargeImage(item.getId())
                    : imageStorageService.getImage(item.getId());
        } catch (FileStorageException e) {
            log.warn("Image file unavailable for item {}", item.getId());
            return Optional.empty();
        }
        try {
            return Optional.of(new McpItemImage(resource.getContentAsByteArray(), McpDtoMapper.name(item),
                    item.getBox().getBoxNumber()));
        } catch (IOException e) {
            log.warn("Image file unreadable for item {}", item.getId());
            throw new McpReadException("Image could not be read");
        }
    }

    private static Pageable page(int limit, Sort sort) {
        checkLimit(limit);
        return PageRequest.of(0, limit, sort);
    }

    private static void checkLimit(int limit) {
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between " + MIN_LIMIT + " and " + MAX_LIMIT);
        }
    }

    private static String requireTerm(String value, String field) {
        String term = optionalTerm(value, field);
        if (term == null) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return term;
    }

    private static String optionalTerm(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String term = value.strip();
        if (term.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException(field + " must not exceed " + MAX_QUERY_LENGTH + " characters");
        }
        return term;
    }
}
