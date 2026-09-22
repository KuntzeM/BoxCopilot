package com.boxcopilot.backend.mcp;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spike item 7 of the design, kept as a standing guarantee: the production migrations run from V1 to the last
 * MCP migration on an empty Postgres, and the schema they leave behind is the one the JDBC stores of Spring
 * Authorization Server address.
 *
 * <p>The other Postgres tests inherit a database that some earlier test class already migrated, so they can
 * only say that the schema exists, not that a fresh installation reaches it. This one owns its container and
 * runs Flyway itself, with no Spring context in the way: if V13 or V14 ever stopped applying cleanly on top of
 * V1 to V12 - a renamed column, a type Postgres rejects, a checksum drift - the failure would land here and
 * name the migration rather than showing up as a context that will not start.
 */
@Testcontainers(disabledWithoutDocker = true)
class McpMigrationTest {

    /** The version {@code users.mcp_allowed} arrives with, and the version that creates the SAS tables. */
    private static final String MCP_ALLOWED_VERSION = "13";
    private static final String OAUTH2_TABLES_VERSION = "14";

    /** Its own container: every other Postgres test runs against a database that is already migrated. */
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    private static JdbcTemplate jdbcTemplate;
    private static MigrateResult migrateResult;
    private static Flyway flyway;

    @BeforeAll
    static void migrateFromScratch() {
        POSTGRES.start();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");
        jdbcTemplate = new JdbcTemplate(dataSource);
        flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();

        assertThat(jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class))
                .as("the container starts with an empty schema").isEmpty();

        migrateResult = flyway.migrate();
    }

    @AfterAll
    static void stopContainer() {
        POSTGRES.stop();
    }

    @Test
    void everyMigrationUpToTheMcpOnesAppliesToAnEmptyDatabase() {
        assertThat(migrateResult.success).isTrue();
        List<String> applied = migrateResult.migrations.stream().map(migration -> migration.version).toList();
        assertThat(applied).startsWith("1").contains(MCP_ALLOWED_VERSION, OAUTH2_TABLES_VERSION);
        assertThat(applied).doesNotHaveDuplicates();
        assertThat(Arrays.stream(flyway.info().all()).map(MigrationInfo::getState))
                .as("no migration is pending, failed or out of order")
                .containsOnly(MigrationState.SUCCESS);
    }

    /** Re-running Flyway on the migrated database is what a restart does, and it has to be a no-op. */
    @Test
    void aSecondMigrateChangesNothing() {
        MigrateResult again = flyway.migrate();

        assertThat(again.success).isTrue();
        assertThat(again.migrationsExecuted).isZero();
    }

    @Test
    void v13AddsMcpAllowedAsANotNullBooleanThatDefaultsToFalse() {
        Map<String, Object> column = jdbcTemplate.queryForMap(
                "SELECT data_type, is_nullable, column_default FROM information_schema.columns "
                        + "WHERE table_name = 'users' AND column_name = 'mcp_allowed'");

        assertThat(column.get("data_type")).isEqualTo("boolean");
        assertThat(column.get("is_nullable")).isEqualTo("NO");
        assertThat(String.valueOf(column.get("column_default"))).isEqualTo("false");
    }

    @Test
    void v14CreatesTheTwoAuthorizationServerTablesAndNoConsentTable() {
        assertThat(jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_name LIKE 'oauth2%'", String.class))
                .containsExactlyInAnyOrder("oauth2_registered_client", "oauth2_authorization");
    }

    /**
     * Postgres has no {@code blob}, and Spring Authorization Server binds these columns as strings: everything
     * that carries serialised state has to be {@code text}, and long enough for a populated authorization.
     */
    @Test
    void everySerialisedColumnIsTextRatherThanBlobOrALimitedVarchar() {
        assertThat(columnTypes("oauth2_authorization")).contains(
                entry("attributes", "text"), entry("authorized_scopes", "text"),
                entry("access_token_value", "text"), entry("access_token_metadata", "text"),
                entry("refresh_token_value", "text"), entry("refresh_token_metadata", "text"),
                entry("authorization_code_value", "text"), entry("authorization_code_metadata", "text"));
        assertThat(columnTypes("oauth2_registered_client")).contains(
                entry("client_settings", "text"), entry("token_settings", "text"),
                entry("redirect_uris", "text"), entry("scopes", "text"),
                entry("client_authentication_methods", "text"), entry("authorization_grant_types", "text"));
        assertThat(jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name LIKE 'oauth2%' "
                        + "AND data_type = 'bytea'", String.class)).isEmpty();
    }

    @Test
    void theAuthorizationsAreIndexedOnEveryColumnTheStoresLookUp() {
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'oauth2_authorization'", String.class);

        assertThat(indexes).anyMatch(index -> index.contains("(access_token_value)"))
                .anyMatch(index -> index.contains("(refresh_token_value)"))
                .anyMatch(index -> index.contains("(authorization_code_value)"))
                .anyMatch(index -> index.contains("(state)"))
                .anyMatch(index -> index.contains("(principal_name)"))
                .anyMatch(index -> index.contains("(registered_client_id)"));
        assertThat(jdbcTemplate.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'oauth2_registered_client'", String.class))
                .anyMatch(index -> index.contains("UNIQUE") && index.contains("(client_id)"));
    }

    /** Deleting a client has to take its authorizations with it; the revocation paths rely on that. */
    @Test
    void theAuthorizationsCascadeWhenTheirClientIsDeleted() {
        jdbcTemplate.update("INSERT INTO oauth2_registered_client (id, client_id, client_name, "
                + "client_authentication_methods, authorization_grant_types, scopes, client_settings, "
                + "token_settings) VALUES ('c1', 'client-1', 'name', 'none', 'authorization_code', 'mcp:read', "
                + "'{}', '{}')");
        jdbcTemplate.update("INSERT INTO oauth2_authorization (id, registered_client_id, principal_name, "
                + "authorization_grant_type) VALUES ('a1', 'c1', '42', 'authorization_code')");

        jdbcTemplate.update("DELETE FROM oauth2_registered_client WHERE id = 'c1'");

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM oauth2_authorization", Long.class)).isZero();
    }

    private static Map<String, String> columnTypes(String table) {
        return jdbcTemplate.query(
                "SELECT column_name, data_type FROM information_schema.columns WHERE table_name = ?",
                rs -> {
                    Map<String, String> types = new java.util.LinkedHashMap<>();
                    while (rs.next()) {
                        types.put(rs.getString("column_name"), rs.getString("data_type"));
                    }
                    return types;
                }, table);
    }

    private static Map.Entry<String, String> entry(String column, String type) {
        return Map.entry(column, type);
    }
}
