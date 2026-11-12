package com.boxcopilot.backend.mcp;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.mcp.abuse.McpRateLimit;
import com.boxcopilot.backend.mcp.abuse.McpRateLimitAudit;
import com.boxcopilot.backend.mcp.abuse.McpRateLimitResponse;
import com.boxcopilot.backend.mcp.authserver.McpAuthorizationServerEndpoints;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The abuse limits on the real chains. The configured limits are tiny here so that a test reaches them in a few
 * requests; the windows are the production ones, and the limiter's own unit test proves how they elapse.
 *
 * <p>Every test uses a client address of its own, because the counters live in one singleton for the whole
 * context, exactly as they do in a running backend.
 */
@TestPropertySource(properties = {
        "app.mcp.rate-limit.calls-per-minute=3",
        "app.mcp.rate-limit.authorization-requests-per-minute=2",
        "app.mcp.rate-limit.failed-token-lookups-per-minute=2"})
class McpRateLimitIntegrationTest extends McpIntegrationTestBase {

    private static final int TOO_MANY_REQUESTS = 429;
    private static final String AUDIT_PREFIX = "MCP rate limit reached ";

    @Autowired
    private MeterRegistry meterRegistry;

    private ListAppender<ILoggingEvent> logAppender;
    private ch.qos.logback.classic.Logger rootLogger;

    /** The audit line is INFO, so the level is left as it is; only the appender is added. */
    @BeforeEach
    void captureAuditLines() {
        rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logAppender = new ListAppender<>();
        logAppender.start();
        rootLogger.addAppender(logAppender);
    }

    @AfterEach
    void releaseLogs() {
        rootLogger.detachAppender(logAppender);
    }

    private List<String> auditLines() {
        return logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.startsWith(AUDIT_PREFIX))
                .toList();
    }

    private double counted(McpRateLimit limit) {
        return meterRegistry.find(McpRateLimitAudit.COUNTER)
                .tag(McpRateLimitAudit.LIMIT_TAG, limit.tag())
                .counters().stream().mapToDouble(counter -> counter.count()).sum();
    }

    private static RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private MvcResult mcp(String bearerToken, String address) throws Exception {
        MockHttpServletRequestBuilder request = post(McpEndpoints.MCP)
                .with(from(address))
                .contentType(MediaType.APPLICATION_JSON)
                .header("Accept", ACCEPT)
                .content(toolsListRequest());
        if (bearerToken != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
        }
        return mockMvc.perform(request).andReturn();
    }

    /** The protected-resource metadata: same chain, same bearer filter, but publicly readable. */
    private MvcResult metadata(String bearerToken, String address) throws Exception {
        MockHttpServletRequestBuilder request =
                get(McpEndpoints.PROTECTED_RESOURCE_METADATA).with(from(address));
        if (bearerToken != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
        }
        return mockMvc.perform(request).andReturn();
    }

    private static void assertRateLimited(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(TOO_MANY_REQUESTS);
        assertThat(result.getResponse().getContentAsString()).isEqualTo(McpRateLimitResponse.BODY);
        assertThat(result.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
    }

    // ---------------------------------------------------------------- per subject

    @Test
    void theSubjectLimitLetsTheConfiguredNumberOfCallsThroughAndThenAnswersGenerically() throws Exception {
        User user = createUser(true, true);
        saveAuthorization(createClient(user.getId()), user.getId(), "rate-subject");

        for (int call = 1; call <= 3; call++) {
            assertThat(mcp("rate-subject", "198.51.100.10").getResponse().getStatus())
                    .as("call %d", call).isEqualTo(200);
        }
        MvcResult blocked = mcp("rate-subject", "198.51.100.10");

        assertRateLimited(blocked);
        assertThat(blocked.getResponse().getContentAsString())
                .doesNotContain(String.valueOf(user.getId()))
                .doesNotContain("rate-subject")
                .doesNotContain("limit");
    }

    /** Claude's egress addresses are shared, so the counter has to follow the token subject, not the address. */
    @Test
    void oneExhaustedSubjectDoesNotBlockAnother() throws Exception {
        User first = createUser(true, true);
        saveAuthorization(createClient(first.getId()), first.getId(), "rate-first");
        User second = createUser(true, true);
        saveAuthorization(createClient(second.getId()), second.getId(), "rate-second");

        for (int call = 1; call <= 3; call++) {
            mcp("rate-first", "198.51.100.11");
        }
        assertThat(mcp("rate-first", "198.51.100.11").getResponse().getStatus()).isEqualTo(TOO_MANY_REQUESTS);

        assertThat(mcp("rate-second", "198.51.100.11").getResponse().getStatus()).isEqualTo(200);
    }

    // ---------------------------------------------------------------- failed token lookups

    @Test
    void failedTokenLookupsAreCountedPerAddressAndTheNextFailureIsRejected() throws Exception {
        for (int attempt = 1; attempt <= 2; attempt++) {
            assertThat(mcp("not-a-token-" + attempt, "198.51.100.12").getResponse().getStatus())
                    .as("attempt %d", attempt).isEqualTo(401);
        }

        assertRateLimited(mcp("not-a-token-3", "198.51.100.12"));
    }

    /**
     * The metadata document is in the same chain and {@code permitAll}, but the bearer filter authenticates a
     * token presented to it just the same and pays the same database query. Guessing there must therefore cost
     * the same budget, or it would be a free lane for guessing tokens.
     */
    @Test
    void bearerTokensSprayedAtTheMetadataDocumentSpendTheSameBudget() throws Exception {
        for (int attempt = 1; attempt <= 2; attempt++) {
            assertThat(metadata("not-a-token-" + attempt, "198.51.100.17").getResponse().getStatus())
                    .as("attempt %d", attempt).isEqualTo(401);
        }

        assertRateLimited(metadata("not-a-token-3", "198.51.100.17"));
        assertRateLimited(mcp("not-a-token-4", "198.51.100.17"));
    }

    /** Without a token the metadata document stays public; the limit must not have closed it. */
    @Test
    void theMetadataDocumentRemainsReadableWithoutAToken() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThat(metadata(null, "198.51.100.18").getResponse().getStatus())
                    .as("attempt %d", attempt).isEqualTo(200);
        }
    }

    /**
     * Claude's egress addresses are shared by every Claude user, so refusing up front would let one caller
     * guessing at tokens lock out everybody else behind the same address. Whether a token resolves cannot be
     * known without the lookup, so a good token is always served and only the guesses are rejected.
     */
    @Test
    void anExhaustedFailureBudgetNeverBlocksAValidToken() throws Exception {
        User user = createUser(true, true);
        saveAuthorization(createClient(user.getId()), user.getId(), "rate-interleaved");

        // Two guesses fill the failure budget, the third is past it; the good token is served throughout.
        // Three rounds is also all the per-subject budget of this test allows, which is a different limit.
        assertThat(mcp("not-a-token-1", "198.51.100.19").getResponse().getStatus()).isEqualTo(401);
        assertThat(mcp("rate-interleaved", "198.51.100.19").getResponse().getStatus()).isEqualTo(200);
        assertThat(mcp("not-a-token-2", "198.51.100.19").getResponse().getStatus()).isEqualTo(401);
        assertThat(mcp("rate-interleaved", "198.51.100.19").getResponse().getStatus()).isEqualTo(200);

        assertRateLimited(mcp("not-a-token-3", "198.51.100.19"));

        assertThat(mcp("rate-interleaved", "198.51.100.19").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void successfulCallsDoNotSpendTheFailureBudget() throws Exception {
        User user = createUser(true, true);
        saveAuthorization(createClient(user.getId()), user.getId(), "rate-success");

        for (int call = 1; call <= 3; call++) {
            assertThat(mcp("rate-success", "198.51.100.13").getResponse().getStatus())
                    .as("call %d", call).isEqualTo(200);
        }

        assertThat(mcp("not-a-token", "198.51.100.13").getResponse().getStatus()).isEqualTo(401);
        assertThat(mcp("not-a-token", "198.51.100.13").getResponse().getStatus()).isEqualTo(401);
        assertRateLimited(mcp("not-a-token", "198.51.100.13"));
    }

    @Test
    void anExhaustedAddressDoesNotBlockAnother() throws Exception {
        mcp("not-a-token", "198.51.100.14");
        mcp("not-a-token", "198.51.100.14");
        assertThat(mcp("not-a-token", "198.51.100.14").getResponse().getStatus()).isEqualTo(TOO_MANY_REQUESTS);

        assertThat(mcp("not-a-token", "198.51.100.15").getResponse().getStatus()).isEqualTo(401);
    }

    /** A request without a bearer token is not a failed lookup: it never reaches the authorization store. */
    @Test
    void requestsWithoutABearerTokenDoNotSpendTheFailureBudget() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThat(mcp(null, "198.51.100.16").getResponse().getStatus()).as("attempt %d", attempt)
                    .isEqualTo(401);
        }

        assertThat(mcp("not-a-token", "198.51.100.16").getResponse().getStatus()).isEqualTo(401);
    }

    // ---------------------------------------------------------------- authorize and token

    @Test
    void theAuthorizeEndpointIsLimitedPerAddress() throws Exception {
        for (int attempt = 1; attempt <= 2; attempt++) {
            assertThat(mockMvc.perform(get(McpAuthorizationServerEndpoints.AUTHORIZE).with(from("198.51.100.20")))
                    .andReturn().getResponse().getStatus()).as("attempt %d", attempt)
                    .isNotEqualTo(TOO_MANY_REQUESTS);
        }

        assertRateLimited(mockMvc.perform(get(McpAuthorizationServerEndpoints.AUTHORIZE)
                .with(from("198.51.100.20"))).andReturn());
    }

    @Test
    void theTokenEndpointIsLimitedPerAddress() throws Exception {
        for (int attempt = 1; attempt <= 2; attempt++) {
            assertThat(mockMvc.perform(post(McpAuthorizationServerEndpoints.TOKEN).with(from("198.51.100.21")))
                    .andReturn().getResponse().getStatus()).as("attempt %d", attempt)
                    .isNotEqualTo(TOO_MANY_REQUESTS);
        }

        assertRateLimited(mockMvc.perform(post(McpAuthorizationServerEndpoints.TOKEN)
                .with(from("198.51.100.21"))).andReturn());
    }

    // ---------------------------------------------------------------- audit and metrics of a rejection

    /**
     * A caller reaching a limit is exactly the event the protection exists for, so none of the three filters
     * may reject in silence. The tag is the limit's fixed name and never the blocked key, so a caller cannot
     * create time series at will.
     */
    @Test
    void theSubjectLimitRecordsItsRejectionOnceWithTheSubjectAndTheAddress() throws Exception {
        User user = createUser(true, true);
        saveAuthorization(createClient(user.getId()), user.getId(), "rate-audited");
        double before = counted(McpRateLimit.TOOL_CALLS);
        for (int call = 1; call <= 3; call++) {
            mcp("rate-audited", "198.51.100.30");
        }

        assertRateLimited(mcp("rate-audited", "198.51.100.30"));

        assertThat(auditLines()).singleElement().asString()
                .isEqualTo("MCP rate limit reached limit=calls sub=" + user.getId() + " ip=198.51.100.30");
        assertThat(counted(McpRateLimit.TOOL_CALLS)).isEqualTo(before + 1);
    }

    @Test
    void theFailedTokenLimitRecordsItsRejectionOnceWithoutASubject() throws Exception {
        double before = counted(McpRateLimit.FAILED_TOKEN_LOOKUPS);
        mcp("not-a-token", "198.51.100.31");
        mcp("not-a-token", "198.51.100.31");

        assertRateLimited(mcp("not-a-token", "198.51.100.31"));

        assertThat(auditLines()).singleElement().asString()
                .isEqualTo("MCP rate limit reached limit=failed_token_lookups sub=- ip=198.51.100.31");
        assertThat(counted(McpRateLimit.FAILED_TOKEN_LOOKUPS)).isEqualTo(before + 1);
    }

    @Test
    void theAddressLimitOnTheAuthorizationServerRecordsItsRejectionOnce() throws Exception {
        double before = counted(McpRateLimit.AUTHORIZATION_REQUESTS);
        mockMvc.perform(get(McpAuthorizationServerEndpoints.AUTHORIZE).with(from("198.51.100.32")));
        mockMvc.perform(get(McpAuthorizationServerEndpoints.AUTHORIZE).with(from("198.51.100.32")));

        assertRateLimited(mockMvc.perform(get(McpAuthorizationServerEndpoints.AUTHORIZE)
                .with(from("198.51.100.32"))).andReturn());

        assertThat(auditLines()).singleElement().asString()
                .isEqualTo("MCP rate limit reached limit=authorization_requests sub=- ip=198.51.100.32");
        assertThat(counted(McpRateLimit.AUTHORIZATION_REQUESTS)).isEqualTo(before + 1);
    }

    /** A call that stays within its limit is not an abuse event and must leave no audit line behind. */
    @Test
    void acceptedCallsRecordNothing() throws Exception {
        User user = createUser(true, true);
        saveAuthorization(createClient(user.getId()), user.getId(), "rate-quiet");

        assertThat(mcp("rate-quiet", "198.51.100.33").getResponse().getStatus()).isEqualTo(200);
        assertThat(mcp("not-a-token", "198.51.100.33").getResponse().getStatus()).isEqualTo(401);

        assertThat(auditLines()).isEmpty();
    }

    /** The consent page and the metadata document are not part of the flood the limit guards against. */
    @Test
    void theMetadataDocumentIsNotRateLimited() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThat(mockMvc.perform(get(McpAuthorizationServerEndpoints.METADATA).with(from("198.51.100.22")))
                    .andReturn().getResponse().getStatus()).as("attempt %d", attempt).isEqualTo(200);
        }
    }
}
