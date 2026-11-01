package com.boxcopilot.backend.mcp;

import com.boxcopilot.backend.mcp.abuse.McpRateLimits;
import com.boxcopilot.backend.mcp.observability.McpAuditLogger;
import com.boxcopilot.backend.mcp.observability.McpToolMetrics;
import tools.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Clock;
import java.util.List;

/** Builds {@link BoxPilotMcpTools} with the collaborators a unit test does not want to state every time. */
final class McpToolsFixtures {

    private McpToolsFixtures() {
    }

    /** MCP properties with the production defaults for everything a tool test does not care about. */
    static McpProperties properties(McpProperties.RateLimit rateLimit) {
        return new McpProperties(true, "https://boxpilot.example", List.of(), null, null, rateLimit, null);
    }

    static McpRateLimits rateLimits(McpProperties.RateLimit rateLimit, Clock clock) {
        return new McpRateLimits(properties(rateLimit), clock);
    }

    static BoxPilotMcpTools tools(McpReadService readService, McpUserGate userGate, ObjectMapper objectMapper) {
        return tools(readService, userGate, objectMapper, rateLimits(null, Clock.systemUTC()),
                new McpAuditLogger(), new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

    static BoxPilotMcpTools tools(McpReadService readService, McpUserGate userGate, ObjectMapper objectMapper,
                                  McpRateLimits rateLimits, McpAuditLogger auditLogger, MeterRegistry registry) {
        return new BoxPilotMcpTools(readService, userGate, objectMapper, rateLimits, auditLogger,
                new McpToolMetrics(registry));
    }
}
