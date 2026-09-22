package com.boxcopilot.backend.mcp;

import com.boxcopilot.backend.mcp.abuse.McpRateLimits;
import com.boxcopilot.backend.mcp.observability.McpAuditLogger;
import com.boxcopilot.backend.mcp.observability.McpToolMetrics;
import com.boxcopilot.backend.mcp.observability.McpToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Every tool call leaves exactly one audit line and one counter, and the daily image quota is spent inside the
 * tool rather than in an HTTP filter.
 */
class BoxPilotMcpToolsObservabilityTest {

    private static final long USER_ID = 42L;
    private static final String ADDRESS = "203.0.113.7";
    private static final McpTransportContext CONTEXT = McpTransportContext.create(Map.of(
            McpTransportContextKeys.USER_ID, USER_ID, McpTransportContextKeys.CLIENT_ADDRESS, ADDRESS));

    private final McpReadService readService = mock(McpReadService.class);
    private final McpUserGate userGate = mock(McpUserGate.class);
    private final McpAuditLogger auditLogger = mock(McpAuditLogger.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MutableTestClock clock = new MutableTestClock();

    private BoxPilotMcpTools toolsWith(McpProperties.RateLimit rateLimit) {
        McpRateLimits rateLimits = McpToolsFixtures.rateLimits(rateLimit, clock);
        return McpToolsFixtures.tools(readService, userGate, new ObjectMapper(), rateLimits, auditLogger,
                registry);
    }

    private BoxPilotMcpTools tools() {
        return toolsWith(null);
    }

    private double counted(String tool, McpToolResult result) {
        return registry.find(McpToolMetrics.COUNTER)
                .tag(McpToolMetrics.TOOL_TAG, tool)
                .tag(McpToolMetrics.RESULT_TAG, result.tag())
                .counters().stream().mapToDouble(counter -> counter.count()).sum();
    }

    private static String text(CallToolResult result) {
        return ((TextContent) result.content().get(0)).text();
    }

    // ---------------------------------------------------------------- metrics and audit per result class

    @Test
    void aSuccessfulCallIsCountedAsOkAndAuditedWithItsHitCount() {
        when(readService.searchItems("lamp", 20)).thenReturn(List.of(
                new McpItemHit(1L, "Lamp", 3, List.of("Office"), false),
                new McpItemHit(2L, "Lamp shade", 4, List.of("Hall"), false)));

        tools().searchItems(CONTEXT, "lamp", null);

        assertThat(counted(BoxPilotMcpTools.SEARCH_ITEMS, McpToolResult.OK)).isEqualTo(1);
        verify(auditLogger).toolCall(BoxPilotMcpTools.SEARCH_ITEMS, USER_ID, ADDRESS, McpToolResult.OK, 2);
        verifyNoMoreInteractions(auditLogger);
    }

    @Test
    void aDeniedCallIsCountedAsDeniedAndStillAudited() {
        doThrow(new McpAccessDeniedException("locked")).when(userGate).requireActiveUser(USER_ID);

        CallToolResult result = tools().searchBoxes(CONTEXT, "kitchen", null, null);

        assertThat(result.isError()).isTrue();
        assertThat(counted(BoxPilotMcpTools.SEARCH_BOXES, McpToolResult.DENIED)).isEqualTo(1);
        verify(auditLogger).toolCall(BoxPilotMcpTools.SEARCH_BOXES, USER_ID, ADDRESS, McpToolResult.DENIED, 0);
    }

    @Test
    void aCallWithoutAUserIsAuditedWithoutASubject() {
        CallToolResult result = tools().searchItems(McpTransportContext.EMPTY, "lamp", null);

        assertThat(result.isError()).isTrue();
        assertThat(counted(BoxPilotMcpTools.SEARCH_ITEMS, McpToolResult.DENIED)).isEqualTo(1);
        verify(auditLogger).toolCall(BoxPilotMcpTools.SEARCH_ITEMS, null, "unknown", McpToolResult.DENIED, 0);
        verify(userGate, never()).requireActiveUser(anyLong());
    }

    @Test
    void rejectedInputAndAFailingReadAreBothCountedAsErrors() {
        tools().getBoxContents(CONTEXT, 0);
        when(readService.boxContents(7)).thenThrow(new IllegalStateException("database down"));
        tools().getBoxContents(CONTEXT, 7);

        assertThat(counted(BoxPilotMcpTools.GET_BOX_CONTENTS, McpToolResult.ERROR)).isEqualTo(2);
        verify(auditLogger, org.mockito.Mockito.times(2))
                .toolCall(eq(BoxPilotMcpTools.GET_BOX_CONTENTS), eq(USER_ID), eq(ADDRESS),
                        eq(McpToolResult.ERROR), anyInt());
    }

    @Test
    void anImageIsAuditedAsOneHitAndAMissingImageAsNone() {
        when(readService.itemImage(7L, ImageSize.THUMBNAIL))
                .thenReturn(Optional.of(new McpItemImage(new byte[] {1, 2, 3}, "Lamp", 3)));
        when(readService.itemImage(8L, ImageSize.THUMBNAIL)).thenReturn(Optional.empty());
        BoxPilotMcpTools tools = tools();

        tools.getItemImage(CONTEXT, 7L, null);
        tools.getItemImage(CONTEXT, 8L, null);

        verify(auditLogger).toolCall(BoxPilotMcpTools.GET_ITEM_IMAGE, USER_ID, ADDRESS, McpToolResult.OK, 1);
        verify(auditLogger).toolCall(BoxPilotMcpTools.GET_ITEM_IMAGE, USER_ID, ADDRESS, McpToolResult.OK, 0);
    }

    // ---------------------------------------------------------------- the daily image quota

    @Test
    void theDailyImageQuotaAnswersWithTheToolsOwnGenericErrorAndCountsAsRateLimited() {
        when(readService.itemImage(anyLong(), any())).thenReturn(Optional.empty());
        BoxPilotMcpTools tools = toolsWith(new McpProperties.RateLimit(0, 2, 0, 0));

        tools.getItemImage(CONTEXT, 7L, null);
        tools.getItemImage(CONTEXT, 7L, null);
        CallToolResult blocked = tools.getItemImage(CONTEXT, 7L, null);

        assertThat(blocked.isError()).isTrue();
        assertThat(text(blocked)).isEqualTo(BoxPilotMcpTools.RATE_LIMIT_ERROR);
        assertThat(counted(BoxPilotMcpTools.GET_ITEM_IMAGE, McpToolResult.RATE_LIMITED)).isEqualTo(1);
        verify(auditLogger).toolCall(BoxPilotMcpTools.GET_ITEM_IMAGE, USER_ID, ADDRESS,
                McpToolResult.RATE_LIMITED, 0);
        verify(readService, org.mockito.Mockito.times(2)).itemImage(anyLong(), any());
    }

    @Test
    void theDailyImageQuotaOpensAgainTheNextDay() {
        when(readService.itemImage(anyLong(), any())).thenReturn(Optional.empty());
        BoxPilotMcpTools tools = toolsWith(new McpProperties.RateLimit(0, 1, 0, 0));
        tools.getItemImage(CONTEXT, 7L, null);
        assertThat(tools.getItemImage(CONTEXT, 7L, null).isError()).isTrue();

        clock.advance(Duration.ofDays(1));

        assertThat(tools.getItemImage(CONTEXT, 7L, null).isError()).isFalse();
    }

    @Test
    void theImageQuotaLeavesTheOtherToolsAlone() {
        when(readService.itemImage(anyLong(), any())).thenReturn(Optional.empty());
        when(readService.searchItems(anyString(), anyInt())).thenReturn(List.of());
        BoxPilotMcpTools tools = toolsWith(new McpProperties.RateLimit(0, 1, 0, 0));

        tools.getItemImage(CONTEXT, 7L, null);
        assertThat(tools.getItemImage(CONTEXT, 7L, null).isError()).isTrue();

        assertThat(tools.searchItems(CONTEXT, "lamp", null).isError()).isFalse();
    }
}
