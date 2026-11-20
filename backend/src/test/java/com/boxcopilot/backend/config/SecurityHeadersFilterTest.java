package com.boxcopilot.backend.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** The headers nginx used to add (frontend/security-headers.inc.template), now written by the backend itself. */
class SecurityHeadersFilterTest {

    private static final String CSP = "default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; "
            + "style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; "
            + "connect-src 'self' data:; worker-src 'self' blob:; frame-ancestors 'none'; base-uri 'self'; "
            + "form-action 'self'";

    private static MockHttpServletResponse run(SecurityHeadersFilter filter, FilterChain chain) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/"), response, chain);
        return response;
    }

    private static MockHttpServletResponse run(SecurityHeadersFilter filter) throws Exception {
        return run(filter, new MockFilterChain());
    }

    @Test
    void thePolicyIsExactlyTheSpecifiedOne() {
        assertThat(SecurityHeadersFilter.CONTENT_SECURITY_POLICY).isEqualTo(CSP);
    }

    @Test
    void enforceModeSendsTheEnforcedPolicyOnly() throws Exception {
        MockHttpServletResponse response = run(new SecurityHeadersFilter("enforce", false));

        assertThat(response.getHeaderValues("Content-Security-Policy")).containsExactly(CSP);
        assertThat(response.getHeader("Content-Security-Policy-Report-Only")).isNull();
    }

    @Test
    void enforceIsMatchedCaseInsensitivelyAndTrimmed() throws Exception {
        MockHttpServletResponse response = run(new SecurityHeadersFilter(" ENFORCE ", false));

        assertThat(response.getHeader("Content-Security-Policy")).isEqualTo(CSP);
    }

    @Test
    void anyOtherModeIsReportOnly() throws Exception {
        for (String mode : new String[] {"report", "report-only", "", null}) {
            MockHttpServletResponse response = run(new SecurityHeadersFilter(mode, false));

            assertThat(response.getHeader("Content-Security-Policy")).as(mode).isNull();
            assertThat(response.getHeaderValues("Content-Security-Policy-Report-Only")).as(mode).containsExactly(CSP);
        }
    }

    @Test
    void basicHeadersAreAlwaysSent() throws Exception {
        MockHttpServletResponse response = run(new SecurityHeadersFilter("enforce", false));

        assertThat(response.getHeaderValues("X-Content-Type-Options")).containsExactly("nosniff");
        assertThat(response.getHeaderValues("X-Frame-Options")).containsExactly("DENY");
        assertThat(response.getHeaderValues("Referrer-Policy")).containsExactly("no-referrer");
    }

    @Test
    void hstsOnlyWhenEnabled() throws Exception {
        assertThat(run(new SecurityHeadersFilter("enforce", false)).getHeader("Strict-Transport-Security")).isNull();
        assertThat(run(new SecurityHeadersFilter("enforce", true)).getHeaderValues("Strict-Transport-Security"))
                .containsExactly("max-age=31536000; includeSubDomains");
    }

    /** The MCP consent page sets its own, stricter policy; it must replace the default, not be combined with it. */
    @Test
    void aPolicySetDownstreamReplacesTheDefault() throws Exception {
        MockHttpServletResponse response = run(new SecurityHeadersFilter("enforce", false),
                (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res)
                        .setHeader("Content-Security-Policy", "default-src 'none'"));

        assertThat(response.getHeaderValues("Content-Security-Policy")).containsExactly("default-src 'none'");
    }

    @Test
    void errorDispatchesAreCoveredToo() {
        assertThat(new SecurityHeadersFilter("enforce", false).shouldNotFilterErrorDispatch()).isFalse();
    }
}
