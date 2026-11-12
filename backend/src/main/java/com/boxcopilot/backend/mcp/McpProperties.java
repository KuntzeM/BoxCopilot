package com.boxcopilot.backend.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.time.Duration;
import java.util.List;

/**
 * MCP feature configuration.
 *
 * @param enabled          master switch; without it no MCP bean exists
 * @param backendUrl       public base URL of this backend (the OAuth issuer and resource identifier); a single
 *                         trailing slash is removed so that paths can simply be appended
 * @param redirectUris     redirect URIs the MCP client is allowed to use
 * @param maxConnectionAge absolute lifetime of an MCP connection, counted from its first authorization; a
 *                         sliding refresh chain cannot outlive it
 * @param cookieSameSite   {@code SameSite} attribute of the pending-authorization cookie; when blank it follows
 *                         {@code server.servlet.session.cookie.same-site}, because the cookie has to travel with
 *                         the same requests the session cookie does
 * @param rateLimit        the abuse limits; absent means the documented defaults
 * @param cleanupCron      cron expression of the daily authorization cleanup; blank means
 *                         {@link #DEFAULT_CLEANUP_CRON}
 * @param spaApiBaseUrl    the API base URL the SPA was built with ({@code VITE_API_BASE_URL}); optional, when
 *                         set its origin must equal the one of {@code backendUrl}
 */
@ConfigurationProperties(prefix = "app.mcp")
public record McpProperties(boolean enabled, String backendUrl, List<String> redirectUris,
                            Duration maxConnectionAge, String cookieSameSite, RateLimit rateLimit,
                            String cleanupCron, String spaApiBaseUrl) {

    /** Convenience for callers that do not set the SPA API base URL. */
    public McpProperties(boolean enabled, String backendUrl, List<String> redirectUris, Duration maxConnectionAge,
                         String cookieSameSite, RateLimit rateLimit, String cleanupCron) {
        this(enabled, backendUrl, redirectUris, maxConnectionAge, cookieSameSite, rateLimit, cleanupCron, null);
    }

    /** 14 days, as specified; kept in sync with the value in {@code application.yml}. */
    public static final Duration DEFAULT_MAX_CONNECTION_AGE = Duration.ofDays(14);

    /** Every night at 03:00 local time, outside the hours anybody uses the inventory. */
    public static final String DEFAULT_CLEANUP_CRON = "0 0 3 * * *";

    @ConstructorBinding
    public McpProperties {
        redirectUris = redirectUris == null ? List.of() : List.copyOf(redirectUris);
        backendUrl = McpUrls.withoutTrailingSlash(backendUrl);
        maxConnectionAge = maxConnectionAge == null ? DEFAULT_MAX_CONNECTION_AGE : maxConnectionAge;
        rateLimit = rateLimit == null ? RateLimit.defaults() : rateLimit;
        cleanupCron = cleanupCron == null || cleanupCron.isBlank() ? DEFAULT_CLEANUP_CRON : cleanupCron;
    }

    /**
     * The abuse limits of the MCP feature, all of them counted in one process's heap. A value that is not
     * positive means "not configured" and falls back to the documented default, because a limit of zero would
     * switch the feature off rather than protect it.
     *
     * @param callsPerMinute                 calls to {@code /mcp} one token subject may make per minute
     * @param imageCallsPerDay               {@code get_item_image} calls one token subject may make per day;
     *                                       large base64 answers are the cost driver
     * @param authorizationRequestsPerMinute requests one IP may make to the authorize and token endpoints per
     *                                       minute
     * @param failedTokenLookupsPerMinute    failed bearer-token lookups one IP may cause at {@code /mcp} per
     *                                       minute; every one of them costs a database query
     */
    public record RateLimit(int callsPerMinute, int imageCallsPerDay, int authorizationRequestsPerMinute,
                            int failedTokenLookupsPerMinute) {

        public static final int DEFAULT_CALLS_PER_MINUTE = 60;
        public static final int DEFAULT_IMAGE_CALLS_PER_DAY = 200;
        public static final int DEFAULT_AUTHORIZATION_REQUESTS_PER_MINUTE = 30;
        public static final int DEFAULT_FAILED_TOKEN_LOOKUPS_PER_MINUTE = 30;

        public RateLimit {
            callsPerMinute = orDefault(callsPerMinute, DEFAULT_CALLS_PER_MINUTE);
            imageCallsPerDay = orDefault(imageCallsPerDay, DEFAULT_IMAGE_CALLS_PER_DAY);
            authorizationRequestsPerMinute =
                    orDefault(authorizationRequestsPerMinute, DEFAULT_AUTHORIZATION_REQUESTS_PER_MINUTE);
            failedTokenLookupsPerMinute =
                    orDefault(failedTokenLookupsPerMinute, DEFAULT_FAILED_TOKEN_LOOKUPS_PER_MINUTE);
        }

        public static RateLimit defaults() {
            return new RateLimit(0, 0, 0, 0);
        }

        private static int orDefault(int configured, int fallback) {
            return configured > 0 ? configured : fallback;
        }
    }
}
