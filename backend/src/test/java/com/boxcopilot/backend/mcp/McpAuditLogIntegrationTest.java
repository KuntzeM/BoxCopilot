package com.boxcopilot.backend.mcp;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.mcp.observability.McpToolMetrics;
import com.boxcopilot.backend.mcp.observability.McpToolResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.RequiredSearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The audit trail of a tool call on the real chains: one line per call with subject, tool, result, hits and
 * address - and no token, no {@code Authorization} header and no client id anywhere in the log, whatever level
 * an installation turns on.
 */
@TestPropertySource(properties = "app.mcp.rate-limit.image-calls-per-day=1")
class McpAuditLogIntegrationTest extends McpIntegrationTestBase {

    private static final String ADDRESS = "203.0.113.42";
    private static final String AUDIT_PREFIX = "MCP tool call ";

    @Autowired
    private MeterRegistry meterRegistry;

    private ListAppender<ILoggingEvent> logAppender;
    private ch.qos.logback.classic.Logger rootLogger;
    private Level originalLevel;

    /**
     * Everything is captured, not just the audit logger: a token leaking through Spring Security, Hibernate or
     * the JDBC layer would be just as fatal, and those can be raised to DEBUG in an installation.
     */
    @BeforeEach
    void captureAllLogs() {
        rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        originalLevel = rootLogger.getLevel();
        rootLogger.setLevel(Level.TRACE);
        logAppender = new ListAppender<>();
        logAppender.start();
        rootLogger.addAppender(logAppender);
    }

    @AfterEach
    void releaseLogs() {
        rootLogger.detachAppender(logAppender);
        rootLogger.setLevel(originalLevel);
    }

    private static RequestPostProcessor fromAuditedAddress() {
        return request -> {
            request.setRemoteAddr(ADDRESS);
            return request;
        };
    }

    private MvcResult call(String bearerToken, String body) throws Exception {
        return mockMvc.perform(post(McpEndpoints.MCP)
                        .with(fromAuditedAddress())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Accept", ACCEPT)
                        .content(body))
                .andReturn();
    }

    private static String toolCall(String tool, String arguments) {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"" + tool
                + "\",\"arguments\":" + arguments + "}}";
    }

    private List<String> auditLines() {
        return logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.startsWith(AUDIT_PREFIX))
                .toList();
    }

    /**
     * Everything the application itself logs. {@code org.postgresql} is the one exception, as in
     * {@code McpClientProvisioningIntegrationTest}: its TRACE level dumps the wire protocol including the bound
     * parameters of every statement, which is a debugging mode of the driver rather than a line this
     * application writes.
     */
    private List<ILoggingEvent> applicationLogs() {
        return logAppender.list.stream()
                .filter(event -> !event.getLoggerName().startsWith("org.postgresql"))
                .toList();
    }

    private double counted(String tool, McpToolResult result) {
        RequiredSearch search = meterRegistry.get(McpToolMetrics.COUNTER)
                .tag(McpToolMetrics.TOOL_TAG, tool)
                .tag(McpToolMetrics.RESULT_TAG, result.tag());
        return search.counter().count();
    }

    @Test
    void oneToolCallLeavesExactlyOneAuditLineWithSubjectToolResultHitsAndAddress() throws Exception {
        User user = createUser(true, true);
        saveAuthorization(createClient(user.getId()), user.getId(), "audit-ok");

        call("audit-ok", toolCall(BoxPilotMcpTools.SEARCH_ITEMS, "{\"query\":\"lamp\",\"limit\":5}"));

        assertThat(auditLines()).singleElement().asString()
                .isEqualTo("MCP tool call sub=" + user.getId() + " tool=" + BoxPilotMcpTools.SEARCH_ITEMS
                        + " result=ok hits=0 ip=" + ADDRESS);
        assertThat(counted(BoxPilotMcpTools.SEARCH_ITEMS, McpToolResult.OK)).isPositive();
    }

    /**
     * A withdrawn user is already rejected by the resource server chain, so the call never becomes a tool call
     * and there is nothing to audit. The tool-level deny - the gate's second, fresh look at the user - is
     * covered by {@code BoxPilotMcpToolsObservabilityTest}, which can make the two reads disagree.
     */
    @Test
    void aCallThatTheChainRejectsNeverBecomesAnAuditedToolCall() throws Exception {
        User user = createUser(true, true);
        saveAuthorization(createClient(user.getId()), user.getId(), "audit-withdrawn");
        user.setMcpAllowed(false);
        userRepository.save(user);

        MvcResult result = call("audit-withdrawn", toolCall(BoxPilotMcpTools.SEARCH_BOXES, "{\"query\":\"x\"}"));

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(auditLines()).isEmpty();
    }

    @Test
    void anExhaustedImageQuotaIsAuditedAndCountedAsRateLimited() throws Exception {
        User user = createUser(true, true);
        saveAuthorization(createClient(user.getId()), user.getId(), "audit-image");

        call("audit-image", toolCall(BoxPilotMcpTools.GET_ITEM_IMAGE, "{\"itemId\":1}"));
        MvcResult blocked = call("audit-image", toolCall(BoxPilotMcpTools.GET_ITEM_IMAGE, "{\"itemId\":1}"));

        assertThat(blocked.getResponse().getStatus()).isEqualTo(200);
        assertThat(blocked.getResponse().getContentAsString()).contains(BoxPilotMcpTools.RATE_LIMIT_ERROR);
        assertThat(auditLines()).hasSize(2).last().asString().contains("result=rate_limited");
        assertThat(counted(BoxPilotMcpTools.GET_ITEM_IMAGE, McpToolResult.RATE_LIMITED)).isPositive();
    }

    /**
     * Arguments that miss a required parameter still reach the tool, so the call is audited and answered with
     * the tool's own generic message - not with the SDK's schema-validator text, which bypasses audit and
     * metrics and is rendered in the JVM's default locale.
     */
    @Test
    void aCallWithInvalidArgumentsIsAuditedAndAnsweredWithTheGenericMessage() throws Exception {
        User user = createUser(true, true);
        saveAuthorization(createClient(user.getId()), user.getId(), "audit-invalid");

        MvcResult result = call("audit-invalid", toolCall(BoxPilotMcpTools.SEARCH_ITEMS, "{}"));

        assertThat(result.getResponse().getContentAsString()).contains(BoxPilotMcpTools.INVALID_INPUT_ERROR);
        assertThat(auditLines()).singleElement().asString().contains("result=error");
        assertThat(counted(BoxPilotMcpTools.SEARCH_ITEMS, McpToolResult.ERROR)).isPositive();
    }

    /** The three things that must never reach a log file, at any level, on any logger. */
    @Test
    void neitherTheTokenNorTheAuthorizationHeaderNorTheClientIdIsEverLogged() throws Exception {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());
        saveAuthorization(client, user.getId(), "audit-secret-token");

        call("audit-secret-token", toolCall(BoxPilotMcpTools.SEARCH_ITEMS, "{\"query\":\"lamp\"}"));

        assertThat(applicationLogs()).isNotEmpty();
        for (ILoggingEvent event : applicationLogs()) {
            assertThat(event.getFormattedMessage()).as("logger %s", event.getLoggerName())
                    .doesNotContain("audit-secret-token")
                    .doesNotContain(client.getClientId())
                    .doesNotContainIgnoringCase("authorization: bearer");
        }
    }
}
