package com.boxcopilot.backend.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class McpDtoShapeTest {

    private static final Set<String> FORBIDDEN_FRAGMENTS = Set.of("imagetoken", "imageurl", "uuid", "token", "url");

    private static final List<Class<? extends Record>> DTOS = List.of(
            McpItemHit.class, McpBoxHit.class, McpBoxContents.class, McpItemRef.class, McpItemImage.class);

    @Test
    void dtosDoNotDeclareTokenUrlOrUuidComponents() {
        DTOS.forEach(dto -> assertThat(componentNames(dto))
                .as("components of %s", dto.getSimpleName())
                .noneMatch(name -> FORBIDDEN_FRAGMENTS.stream().anyMatch(name::contains)));
    }

    @Test
    void itemHitExposesExactlyTheDocumentedFields() {
        assertThat(componentNames(McpItemHit.class))
                .containsExactly("itemid", "name", "boxnumber", "rooms", "hasimage");
    }

    @Test
    void boxHitExposesExactlyTheDocumentedFields() {
        assertThat(componentNames(McpBoxHit.class)).containsExactly("boxnumber", "rooms", "flags", "itemcount");
    }

    @Test
    void boxContentsExposesExactlyTheDocumentedFields() {
        assertThat(componentNames(McpBoxContents.class)).containsExactly("box", "description", "items");
    }

    @Test
    void itemRefExposesExactlyTheDocumentedFields() {
        assertThat(componentNames(McpItemRef.class)).containsExactly("itemid", "name", "hasimage");
    }

    @Test
    void itemImageExposesExactlyTheDocumentedFields() {
        assertThat(componentNames(McpItemImage.class)).containsExactly("content", "name", "boxnumber");
    }

    @Test
    void imageSizeHasThumbnailAndLarge() {
        assertThat(ImageSize.values()).containsExactly(ImageSize.THUMBNAIL, ImageSize.LARGE);
    }

    @Test
    void collectionComponentsAreImmutableCopies() {
        java.util.ArrayList<String> rooms = new java.util.ArrayList<>(List.of("Kitchen"));
        McpItemHit hit = new McpItemHit(1L, "lamp", 1, rooms, false);
        rooms.add("Cellar");

        assertThat(hit.rooms()).containsExactly("Kitchen");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> hit.rooms().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void itemImageProtectsItsBytesAndDoesNotPrintThem() {
        byte[] bytes = {1, 2, 3};
        McpItemImage image = new McpItemImage(bytes, "lamp", 1);
        bytes[0] = 9;
        image.content()[1] = 9;

        assertThat(image.content()).containsExactly(1, 2, 3);
        assertThat(image.toString()).doesNotContain("[B@").contains("lamp");
        assertThat(image).isEqualTo(new McpItemImage(new byte[]{1, 2, 3}, "lamp", 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"thumbnail", "THUMBNAIL", "Large", "large"})
    void imageSizeParsesCaseInsensitively(String value) {
        assertThat(ImageSize.fromString(value)).isNotNull();
    }

    @Test
    void imageSizeRejectsUnknownValues() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ImageSize.fromString("huge"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<String> componentNames(Class<? extends Record> type) {
        return Stream.of(type.getRecordComponents())
                .map(RecordComponent::getName)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .toList();
    }
}
