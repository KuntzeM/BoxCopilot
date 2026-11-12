package com.boxcopilot.backend.mcp.observability;

import com.boxcopilot.backend.mcp.abuse.McpClientAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Writes one audit line per MCP tool call: who called, which tool, how it ended, how many hits came back and
 * from which address.
 *
 * <p>What is deliberately absent is as much part of this class as what it writes. The access token, the
 * {@code Authorization} header and the per-user client id never appear in a log line: the client id is a
 * credential a user has to keep, and a token in a log file is a token in every backup of that log file. This
 * logger is handed none of them, so there is nothing here that could leak them.
 */
@Component
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
public class McpAuditLogger {

    /** Stands in for a caller whose user id could not be established, so the field is never empty. */
    static final String NO_SUBJECT = "-";

    private static final Logger log = LoggerFactory.getLogger(McpAuditLogger.class);

    /**
     * @param tool          the tool name as the client called it
     * @param subject       the internal user id, or {@code null} if the call carried no usable identity
     * @param clientAddress the resolved client address
     * @param result        how the call ended
     * @param hits          how many items or boxes were returned; {@code 0} or {@code 1} for a single image
     */
    public void toolCall(String tool, Long subject, String clientAddress, McpToolResult result, int hits) {
        log.info("MCP tool call sub={} tool={} result={} hits={} ip={}",
                subject == null ? NO_SUBJECT : subject, tool, result.tag(), hits,
                clientAddress == null ? McpClientAddress.UNKNOWN : clientAddress);
    }
}
