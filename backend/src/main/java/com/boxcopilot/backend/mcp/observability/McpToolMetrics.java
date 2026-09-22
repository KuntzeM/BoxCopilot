package com.boxcopilot.backend.mcp.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * One Micrometer counter per tool and result class. It publishes no endpoint of its own: the counters are
 * visible wherever the registry is, which today is the in-process registry and, the moment Actuator and
 * Prometheus are in operation, their scrape endpoint.
 */
@Component
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
public class McpToolMetrics {

    /** Name of the counter; the tags {@link #TOOL_TAG} and {@link #RESULT_TAG} separate the series. */
    public static final String COUNTER = "boxpilot.mcp.tool.calls";

    public static final String TOOL_TAG = "tool";
    public static final String RESULT_TAG = "result";

    private final MeterRegistry registry;

    public McpToolMetrics(MeterRegistry registry) {
        Assert.notNull(registry, "registry cannot be null");
        this.registry = registry;
    }

    public void count(String tool, McpToolResult result) {
        registry.counter(COUNTER, TOOL_TAG, tool, RESULT_TAG, result.tag()).increment();
    }
}
