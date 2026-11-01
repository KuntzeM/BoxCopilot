package com.boxcopilot.backend.mcp;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ImageContent;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.ai.mcp.annotation.McpTool;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BoxPilotMcpToolsTest {

    private static final long USER_ID = 42L;
    private static final McpTransportContext CONTEXT =
            McpTransportContext.create(Map.of(McpTransportContextKeys.USER_ID, USER_ID));
    private static final String SECRET = "secret /var/data/images/1.jpg jdbc:postgresql://db password";

    private final McpReadService readService = mock(McpReadService.class);
    private final McpUserGate userGate = mock(McpUserGate.class);
    private final JsonMapper objectMapper = new JsonMapper();
    private final BoxPilotMcpTools tools = McpToolsFixtures.tools(readService, userGate, objectMapper);

    private static String text(CallToolResult result, int index) {
        return ((TextContent) result.content().get(index)).text();
    }

    private JsonNode json(CallToolResult result) throws Exception {
        return objectMapper.readTree(text(result, 0));
    }

    private static void assertGenericError(CallToolResult result) {
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).hasSize(1);
        assertThat(text(result, 0)).isEqualTo(BoxPilotMcpTools.GENERIC_ERROR);
    }

    // ---- search_items

    @Test
    void searchItemsReturnsStructuredJsonAfterCheckingTheGate() throws Exception {
        when(readService.searchItems("lamp", 5))
                .thenReturn(List.of(new McpItemHit(7L, "Desk lamp", 12, List.of("Office", "Hall"), true)));

        CallToolResult result = tools.searchItems(CONTEXT, "lamp", 5);

        assertThat(result.isError()).isFalse();
        JsonNode item = json(result).get("items").get(0);
        assertThat(item.get("itemId").asLong()).isEqualTo(7L);
        assertThat(item.get("name").asString()).isEqualTo("Desk lamp");
        assertThat(item.get("boxNumber").asInt()).isEqualTo(12);
        assertThat(item.get("rooms").get(1).asString()).isEqualTo("Hall");
        assertThat(item.get("hasImage").asBoolean()).isTrue();
        InOrder order = inOrder(userGate, readService);
        order.verify(userGate).requireActiveUser(USER_ID);
        order.verify(readService).searchItems("lamp", 5);
    }

    @Test
    void searchItemsUsesDefaultLimitOf20() {
        when(readService.searchItems(anyString(), anyInt())).thenReturn(List.of());

        tools.searchItems(CONTEXT, "lamp", null);

        verify(readService).searchItems("lamp", 20);
    }

    @Test
    void searchItemsRejectsInvalidInputWithoutCallingTheService() {
        String tooLong = "x".repeat(101);
        for (Object[] args : new Object[][] {
                {null, 5}, {"", 5}, {"   ", 5}, {tooLong, 5}, {"lamp", 0}, {"lamp", 51}, {"lamp", -1}}) {
            CallToolResult result = tools.searchItems(CONTEXT, (String) args[0], (Integer) args[1]);

            assertThat(result.isError()).isTrue();
            assertThat(text(result, 0)).isEqualTo(BoxPilotMcpTools.INVALID_INPUT_ERROR);
        }
        verifyNoInteractions(readService);
    }

    @Test
    void searchItemsAcceptsBoundaryValues() {
        when(readService.searchItems(anyString(), anyInt())).thenReturn(List.of());
        String hundred = "x".repeat(100);

        assertThat(tools.searchItems(CONTEXT, hundred, 1).isError()).isFalse();
        assertThat(tools.searchItems(CONTEXT, "a", 50).isError()).isFalse();
    }

    // ---- gate and context (checked on every tool)

    @Test
    void everyToolFailsClosedWithoutUserInContext() {
        for (McpTransportContext context : new McpTransportContext[] {
                null, McpTransportContext.EMPTY,
                McpTransportContext.create(Map.of(McpTransportContextKeys.USER_ID, "42"))}) {
            assertGenericError(tools.searchItems(context, "lamp", 5));
            assertGenericError(tools.getItemImage(context, 1L, null));
            assertGenericError(tools.searchBoxes(context, "5", null, 5));
            assertGenericError(tools.getBoxContents(context, 5));
        }
        verifyNoInteractions(userGate, readService);
    }

    @Test
    void everyToolFailsClosedWhenTheGateDenies() {
        doThrow(new McpAccessDeniedException(SECRET)).when(userGate).requireActiveUser(USER_ID);

        assertGenericError(tools.searchItems(CONTEXT, "lamp", 5));
        assertGenericError(tools.getItemImage(CONTEXT, 1L, null));
        assertGenericError(tools.searchBoxes(CONTEXT, "5", null, 5));
        assertGenericError(tools.getBoxContents(CONTEXT, 5));
        verifyNoInteractions(readService);
    }

    @Test
    void everyToolFailsClosedWhenTheGateItselfBreaks() {
        doThrow(new IllegalArgumentException(SECRET)).when(userGate).requireActiveUser(USER_ID);

        assertGenericError(tools.searchItems(CONTEXT, "lamp", 5));
        assertGenericError(tools.getItemImage(CONTEXT, 1L, null));
        assertGenericError(tools.searchBoxes(CONTEXT, "5", null, 5));
        assertGenericError(tools.getBoxContents(CONTEXT, 5));
        verifyNoInteractions(readService);
    }

    @Test
    void gateIsCheckedBeforeInputValidation() {
        doThrow(new McpAccessDeniedException("denied")).when(userGate).requireActiveUser(USER_ID);

        assertGenericError(tools.searchItems(CONTEXT, "", 5));
    }

    @Test
    void serviceFailuresBecomeFixedGenericErrors() {
        when(readService.searchItems(anyString(), anyInt())).thenThrow(new McpReadException(SECRET));
        when(readService.itemImage(anyLong(), any())).thenThrow(new RuntimeException(SECRET));
        when(readService.searchBoxes(any(), any(), anyInt())).thenThrow(new IllegalStateException(SECRET));
        when(readService.boxContents(anyInt())).thenThrow(new NullPointerException(SECRET));

        assertGenericError(tools.searchItems(CONTEXT, "lamp", 5));
        assertGenericError(tools.getItemImage(CONTEXT, 1L, "thumbnail"));
        assertGenericError(tools.searchBoxes(CONTEXT, "5", null, 5));
        assertGenericError(tools.getBoxContents(CONTEXT, 5));
    }

    @Test
    void illegalArgumentFromTheServiceIsAFixedInvalidInputError() {
        when(readService.searchItems(anyString(), anyInt())).thenThrow(new IllegalArgumentException(SECRET));

        CallToolResult result = tools.searchItems(CONTEXT, "lamp", 5);

        assertThat(result.isError()).isTrue();
        assertThat(text(result, 0)).isEqualTo(BoxPilotMcpTools.INVALID_INPUT_ERROR);
    }

    @Test
    void errorTextsDoNotLeakAnything() {
        assertThat(BoxPilotMcpTools.GENERIC_ERROR).doesNotContain("secret", "/", "jdbc", "password");
        assertThat(BoxPilotMcpTools.INVALID_INPUT_ERROR).doesNotContain("secret", "/", "jdbc", "password");
    }

    // ---- get_item_image

    @Test
    void getItemImageReturnsTextAndBase64JpegImage() throws Exception {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, 1, 2, 3};
        when(readService.itemImage(9L, ImageSize.THUMBNAIL))
                .thenReturn(Optional.of(new McpItemImage(jpeg, "Desk lamp", 12)));

        CallToolResult result = tools.getItemImage(CONTEXT, 9L, null);

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).hasSize(2);
        JsonNode description = json(result);
        assertThat(description.get("name").asString()).isEqualTo("Desk lamp");
        assertThat(description.get("boxNumber").asInt()).isEqualTo(12);
        ImageContent image = (ImageContent) result.content().get(1);
        assertThat(image.mimeType()).isEqualTo("image/jpeg");
        assertThat(Base64.getDecoder().decode(image.data())).isEqualTo(jpeg);
        verify(userGate).requireActiveUser(USER_ID);
    }

    @Test
    void getItemImageHonoursLargeSizeInAnyCase() {
        when(readService.itemImage(anyLong(), any())).thenReturn(Optional.empty());

        tools.getItemImage(CONTEXT, 9L, "LARGE");

        verify(readService).itemImage(9L, ImageSize.LARGE);
    }

    @Test
    void getItemImageWithoutImageIsATextOnlyAnswer() throws Exception {
        when(readService.itemImage(anyLong(), any())).thenReturn(Optional.empty());

        CallToolResult result = tools.getItemImage(CONTEXT, 9L, "thumbnail");

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).hasSize(1).allMatch(TextContent.class::isInstance);
        assertThat(json(result).get("message").asString()).isEqualTo(BoxPilotMcpTools.NO_IMAGE_MESSAGE);
    }

    @Test
    void getItemImageRejectsUnknownSizeAndMissingItemId() {
        CallToolResult unknownSize = tools.getItemImage(CONTEXT, 9L, "huge");
        CallToolResult missingId = tools.getItemImage(CONTEXT, null, "thumbnail");

        assertThat(text(unknownSize, 0)).isEqualTo(BoxPilotMcpTools.INVALID_INPUT_ERROR);
        assertThat(text(missingId, 0)).isEqualTo(BoxPilotMcpTools.INVALID_INPUT_ERROR);
        verify(readService, never()).itemImage(anyLong(), any());
    }

    // ---- search_boxes

    @Test
    void searchBoxesReturnsStructuredJson() throws Exception {
        when(readService.searchBoxes("kitchen", "Attic", 3))
                .thenReturn(List.of(new McpBoxHit(5, List.of("Attic"), List.of("fragile"), 4L)));

        CallToolResult result = tools.searchBoxes(CONTEXT, "kitchen", "Attic", 3);

        assertThat(result.isError()).isFalse();
        JsonNode box = json(result).get("boxes").get(0);
        assertThat(box.get("boxNumber").asInt()).isEqualTo(5);
        assertThat(box.get("rooms").get(0).asString()).isEqualTo("Attic");
        assertThat(box.get("flags").get(0).asString()).isEqualTo("fragile");
        assertThat(box.get("itemCount").asLong()).isEqualTo(4L);
    }

    @Test
    void searchBoxesWorksWithOnlyOneOfQueryAndRoomAndDefaultLimit() {
        when(readService.searchBoxes(any(), any(), anyInt())).thenReturn(List.of());

        tools.searchBoxes(CONTEXT, "kitchen", null, null);
        tools.searchBoxes(CONTEXT, null, "Attic", null);

        verify(readService).searchBoxes("kitchen", null, 20);
        verify(readService).searchBoxes(null, "Attic", 20);
    }

    @Test
    void searchBoxesRejectsInvalidInputWithoutCallingTheService() {
        String tooLong = "x".repeat(101);
        for (Object[] args : new Object[][] {
                {null, null, 5}, {"", null, 5}, {" ", " ", 5}, {tooLong, null, 5}, {null, tooLong, 5},
                {"a", null, 0}, {"a", null, 51}}) {
            CallToolResult result = tools.searchBoxes(CONTEXT, (String) args[0], (String) args[1], (Integer) args[2]);

            assertThat(text(result, 0)).isEqualTo(BoxPilotMcpTools.INVALID_INPUT_ERROR);
        }
        verifyNoInteractions(readService);
    }

    // ---- get_box_contents

    @Test
    void getBoxContentsReturnsBoxAndItems() throws Exception {
        McpBoxHit box = new McpBoxHit(5, List.of("Attic"), List.of(), 2L);
        when(readService.boxContents(5)).thenReturn(Optional.of(new McpBoxContents(
                box, "Winter stuff", List.of(new McpItemRef(1L, "Scarf", false), new McpItemRef(2L, "Hat", true)))));

        CallToolResult result = tools.getBoxContents(CONTEXT, 5);

        assertThat(result.isError()).isFalse();
        JsonNode body = json(result);
        assertThat(body.get("box").get("boxNumber").asInt()).isEqualTo(5);
        assertThat(body.get("description").asString()).isEqualTo("Winter stuff");
        assertThat(body.get("items").get(1).get("name").asString()).isEqualTo("Hat");
        assertThat(body.get("items").get(1).get("hasImage").asBoolean()).isTrue();
    }

    @Test
    void getBoxContentsForUnknownBoxIsATextAnswer() throws Exception {
        when(readService.boxContents(anyInt())).thenReturn(Optional.empty());

        CallToolResult result = tools.getBoxContents(CONTEXT, 99);

        assertThat(result.isError()).isFalse();
        assertThat(json(result).get("message").asString()).isEqualTo(BoxPilotMcpTools.BOX_NOT_FOUND_MESSAGE);
    }

    @Test
    void getBoxContentsRejectsMissingOrNonPositiveBoxNumber() {
        assertThat(text(tools.getBoxContents(CONTEXT, null), 0)).isEqualTo(BoxPilotMcpTools.INVALID_INPUT_ERROR);
        assertThat(text(tools.getBoxContents(CONTEXT, 0), 0)).isEqualTo(BoxPilotMcpTools.INVALID_INPUT_ERROR);
        verify(readService, never()).boxContents(eq(0));
    }

    // ---- tool metadata

    @Test
    void allFourToolsAreReadOnlyAndWarnAboutUntrustedData() {
        List<Method> toolMethods = Arrays.stream(BoxPilotMcpTools.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(McpTool.class))
                .toList();

        assertThat(toolMethods).extracting(method -> method.getAnnotation(McpTool.class).name())
                .containsExactlyInAnyOrder("search_items", "get_item_image", "search_boxes", "get_box_contents");
        for (Method method : toolMethods) {
            McpTool tool = method.getAnnotation(McpTool.class);
            assertThat(tool.annotations().readOnlyHint()).as(tool.name()).isTrue();
            assertThat(tool.annotations().destructiveHint()).as(tool.name()).isFalse();
            assertThat(tool.annotations().idempotentHint()).as(tool.name()).isTrue();
            assertThat(tool.annotations().openWorldHint()).as(tool.name()).isFalse();
            assertThat(tool.description()).as(tool.name()).containsIgnoringCase("untrusted");
        }
    }
}
