package com.boxcopilot.backend.mcp;

import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.mcp.security.McpAuthorizationCleanup;
import com.boxcopilot.backend.mcp.security.McpOAuth2Attributes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.CronTask;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The nightly cleanup against the production schema. Rows are seeded through the real store, so the columns the
 * rules read are the ones Spring Authorization Server actually writes.
 */
class McpAuthorizationCleanupPostgresTest extends McpIntegrationTestBase {

    private static final Duration MAX_AGE = McpProperties.DEFAULT_MAX_CONNECTION_AGE;

    @Autowired
    private McpAuthorizationCleanup cleanup;

    @Autowired
    private ScheduledAnnotationBeanPostProcessor scheduledTasks;

    private final Instant now = Instant.now();

    private OAuth2Authorization.Builder builder(RegisteredClient client, long userId) {
        return OAuth2Authorization.withRegisteredClient(client)
                .id(UUID.randomUUID().toString())
                .principalName(String.valueOf(userId))
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizedScopes(Set.of(McpOAuth2Attributes.SCOPE_READ));
    }

    /** Issued far enough in the past that even an already expired token was still issued before it expired. */
    private static final Duration ISSUED_AGO = Duration.ofHours(2);

    private OAuth2AccessToken accessToken(String value, Duration untilExpiry) {
        return new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, value,
                now.minus(ISSUED_AGO), now.plus(untilExpiry), Set.of(McpOAuth2Attributes.SCOPE_READ));
    }

    private OAuth2RefreshToken refreshToken(String value, Duration untilExpiry) {
        return new OAuth2RefreshToken(value, now.minus(ISSUED_AGO), now.plus(untilExpiry));
    }

    private OAuth2AuthorizationCode code(String value, Duration untilExpiry) {
        return new OAuth2AuthorizationCode(value, now.minus(ISSUED_AGO), now.plus(untilExpiry));
    }

    private String save(OAuth2Authorization authorization) {
        authorizationService.save(authorization);
        return authorization.getId();
    }

    private List<String> remainingIds() {
        return jdbcTemplate.queryForList("SELECT id FROM oauth2_authorization", String.class);
    }

    @Test
    void itRemovesOnlyTheAuthorizationsThatCanNeverBeUsedAgain() {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());

        String healthy = save(builder(client, user.getId())
                .token(accessToken("clean-healthy", Duration.ofMinutes(30)))
                .refreshToken(refreshToken("clean-healthy-refresh", Duration.ofDays(7)))
                .build());
        String accessOnlyValid = save(builder(client, user.getId())
                .token(accessToken("clean-access-valid", Duration.ofMinutes(30)))
                .build());
        String codeStillValid = save(builder(client, user.getId())
                .token(code("clean-code-valid", Duration.ofSeconds(60)))
                .build());
        String codeAlreadyRedeemed = save(builder(client, user.getId())
                .token(code("clean-code-redeemed", Duration.ofSeconds(-60)))
                .token(accessToken("clean-code-redeemed-access", Duration.ofMinutes(30)))
                .refreshToken(refreshToken("clean-code-redeemed-refresh", Duration.ofDays(7)))
                .build());

        save(builder(client, user.getId())
                .token(accessToken("clean-refresh-expired", Duration.ofMinutes(-30)))
                .refreshToken(refreshToken("clean-refresh-expired-refresh", Duration.ofMinutes(-1)))
                .build());
        save(builder(client, user.getId())
                .token(accessToken("clean-access-expired", Duration.ofMinutes(-1)))
                .build());
        save(builder(client, user.getId())
                .token(accessToken("clean-too-old", Duration.ofMinutes(30)))
                .refreshToken(refreshToken("clean-too-old-refresh", Duration.ofDays(7)))
                .attribute(McpOAuth2Attributes.STARTED_AT,
                        String.valueOf(now.minus(MAX_AGE).minus(Duration.ofDays(1)).getEpochSecond()))
                .build());
        save(builder(client, user.getId())
                .token(code("clean-code-expired", Duration.ofSeconds(-1)))
                .build());

        assertThat(remainingIds()).hasSize(8);
        cleanup.removeExpiredAuthorizations();

        assertThat(remainingIds())
                .containsExactlyInAnyOrder(healthy, accessOnlyValid, codeStillValid, codeAlreadyRedeemed);
    }

    @Test
    void itLeavesAHealthyTableAlone() {
        User user = createUser(true, true);
        String healthy = save(builder(createClient(user.getId()), user.getId())
                .token(accessToken("clean-nothing-to-do", Duration.ofMinutes(30)))
                .refreshToken(refreshToken("clean-nothing-to-do-refresh", Duration.ofDays(7)))
                .build());

        cleanup.removeExpiredAuthorizations();

        assertThat(remainingIds()).containsExactly(healthy);
    }

    /** The job has to be on the scheduler, not merely callable; nothing here waits for it to fire. */
    @Test
    void theCleanupIsRegisteredAsACronTaskAtTheConfiguredTime() {
        assertThat(scheduledTasks.getScheduledTasks())
                .map(task -> task.getTask())
                .filteredOn(CronTask.class::isInstance)
                .map(task -> ((CronTask) task).getExpression())
                .contains(McpProperties.DEFAULT_CLEANUP_CRON);
    }
}
