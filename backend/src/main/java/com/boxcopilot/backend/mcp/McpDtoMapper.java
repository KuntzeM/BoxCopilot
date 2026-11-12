package com.boxcopilot.backend.mcp;

import com.boxcopilot.backend.domain.Box;
import com.boxcopilot.backend.domain.Item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Maps entities to MCP output records, sanitising all free text on the way out. */
final class McpDtoMapper {

    static final int NAME_MAX_LENGTH = 200;
    static final int ROOM_MAX_LENGTH = 100;
    static final int DESCRIPTION_MAX_LENGTH = 500;

    static final Comparator<Item> ITEM_ORDER = Comparator
            .comparing((Item item) -> item.getName() == null ? "" : item.getName(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Item::getId);

    private McpDtoMapper() {
    }

    static boolean hasBoxNumber(Item item) {
        return item.getBox() != null && item.getBox().getBoxNumber() != null;
    }

    static McpItemHit toItemHit(Item item) {
        Box box = item.getBox();
        return new McpItemHit(item.getId(), name(item), box.getBoxNumber(), rooms(box), hasImage(item));
    }

    static McpItemRef toItemRef(Item item) {
        return new McpItemRef(item.getId(), name(item), hasImage(item));
    }

    static McpBoxHit toBoxHit(Box box, long itemCount) {
        return new McpBoxHit(box.getBoxNumber(), rooms(box), flags(box), itemCount);
    }

    static String name(Item item) {
        return McpTextSanitizer.clean(item.getName(), NAME_MAX_LENGTH);
    }

    static String description(Box box) {
        return McpTextSanitizer.clean(box.getDescription(), DESCRIPTION_MAX_LENGTH);
    }

    static boolean hasImage(Item item) {
        return item.getImagePath() != null && !item.getImagePath().isBlank();
    }

    private static List<String> rooms(Box box) {
        List<String> rooms = new ArrayList<>(2);
        for (String room : new String[]{box.getCurrentRoom(), box.getTargetRoom()}) {
            String cleaned = McpTextSanitizer.clean(room, ROOM_MAX_LENGTH);
            if (!cleaned.isEmpty() && !rooms.contains(cleaned)) {
                rooms.add(cleaned);
            }
        }
        return rooms;
    }

    private static List<String> flags(Box box) {
        List<String> flags = new ArrayList<>(4);
        addIf(flags, box.getIsFragile(), "fragile");
        addIf(flags, box.getNoStack(), "no_stack");
        addIf(flags, box.getIsMovedToTarget(), "moved_to_target");
        addIf(flags, box.getLabelPrinted(), "label_printed");
        return flags;
    }

    private static void addIf(List<String> flags, Boolean set, String flag) {
        if (Boolean.TRUE.equals(set)) {
            flags.add(flag);
        }
    }
}
