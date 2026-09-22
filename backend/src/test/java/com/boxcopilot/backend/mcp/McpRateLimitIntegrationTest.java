package com.boxcopilot.backend.mcp;

import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.mcp.abuse.McpRateLimitResponse;
import com.boxcopilot.backend.mcp.authserver.McpAuthorizationServerEndpoints;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

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
    void failedTokenLookupsAreCountedPerAddressAndStopFurtherDatabaseWork() throws Exception {
        for (int attempt = 1; attempt <= 2; attempt++) {
            assertThat(mcp("not-a-token-" + attempt, "198.51.100.12").getResponse().getStatus())
                    .as("attempt %d", attempt).isEqualTo(401);
        }

        assertRateLimited(mcp("not-a-token-3", "198.51.100.12"));
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

    /** The consent page and the metadata document are not part of the flood the limit guards against. */
    @Test
    void theMetadataDocumentIsNotRateLimited() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThat(mockMvc.perform(get(McpAuthorizationServerEndpoints.METADATA).with(from("198.51.100.22")))
                    .andReturn().getResponse().getStatus()).as("attempt %d", attempt).isEqualTo(200);
        }
    }
}
