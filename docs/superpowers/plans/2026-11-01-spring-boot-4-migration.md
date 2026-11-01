# Spring Boot 4.1 Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move `backend/` from Spring Boot 3.5.16 / Spring AI 1.1.8 to Spring Boot 4.1.2 / Spring AI 2.0.2 with no loss of functionality, and bring `backend/Dockerfile`, `docker-compose.yml`, `.env.example` and docs in line.

**Architecture:** One hop to Boot 4.1.x (not 4.0.x, whose OSS support ends 2026-12-31). The work is layered so that each layer ends with a green, narrowly scoped test run: build files, then Jackson 3, then renamed auto-configuration and properties, then Security 7, then Spring AI 2 / MCP SDK 2, then JPA/Hibernate 7 / Flyway 12, then tests, then runtime and containers. Jackson 2 compatibility fallbacks (`spring-boot-jackson2`, `*-classic` starters) are used only as temporary scaffolding inside a task and are gone before it ends.

**Tech Stack (target, from the Boot 4.1.2 BOM unless noted):** Spring Framework 7.0.10, Spring Security 7.1.2 (includes Authorization Server), Spring Session 4.1.1, Spring Data 2026.0.2, Hibernate 7.4.13, Jackson 3.1.7 (`tools.jackson`), Tomcat 11.0.27, Flyway 12.4.0, PostgreSQL JDBC 42.7.14, H2 2.4.240, Lettuce 7.5.2, Testcontainers 2.0.5, JUnit 6.0.3, Mockito 5.23.0; Spring AI 2.0.2 with MCP Java SDK 2.1.0 (`mcp`, `mcp-spring-webmvc` 2.0.2); Java 21 stays.

**Spec:** none. Source of truth is the [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide), the Spring Security 7.0 migration guide, the Spring AI 2.0 upgrade notes, and the current code. Prior art for plan style: `docs/superpowers/plans/2026-09-21-mcp-server-sprint.md`.

## Global Constraints

- Preserve controller -> service -> repository layering and existing transaction boundaries (root `CLAUDE.md`).
- Never edit an applied Flyway migration (V1..V14). No schema change is planned; if Hibernate 7 validation demands one, add V15.
- Authentication, authorization, sessions, public share tokens, image access, proxy headers and MCP are security-sensitive: every behaviour change needs a regression test (`backend/CLAUDE.md`, `config/CLAUDE.md`, `mcp/CLAUDE.md`).
- MCP stays off unless `app.mcp.enabled=true`; no new `SecurityFilterChain` without ordering and matcher-ownership tests.
- Production startup guards (admin password, Redis password) stay intact.
- Keep backend DTOs and frontend types in sync. No API contract change is intended; JSON output of every endpoint must stay byte-compatible (Jackson 3 defaults differ, see Task 3).
- Run `graphify update .` after source changes. Do not hand-edit `graphify-out/`. Do not commit credentials, `target/`, `node_modules/`, logs, runtime data.
- Run Maven from `backend/`, npm from `frontend/`. Narrowest test first, then the module suite.
- Docker Desktop must be running for the Testcontainers tests (207 of 847 tests were skipped in the 3.5.16 run because the daemon was down). A run with skipped Postgres tests does not count as green.
- Support target: Spring Framework 7.0.10 and MCP SDK 2.1.0 are the fixed versions for GHSA-j9f9-w8pj-32f8, GHSA-pc63-qcmh-9cmg and CVE-2026-35568; re-scan at the end to prove they are gone.

## Review Focus

Failure modes the migration implies that no existing test exercises, most likely first:

1. **Existing MCP authorizations in `oauth2_authorization`** were serialized by Spring Security 6 (Jackson 2, allowlist mixins). After the upgrade the JDBC service reads Jackson 3 JSON; a row written by 3.5 must still load, or fail closed (token rejected, client re-connects), never 500 or accept. Pinned in Task 5.
2. **Spring Session in Redis**: sessions written by 3.5 must not break 4.1 (deserialization error loops on every request). Expected outcome is "logged out once"; pinned in Task 9.
3. **Auto-configuration exclusions by class name** in `application-dev.yml`, `application-test.yml`, `application-mcp-test.yml` and `@ImportAutoConfiguration`/`AutoConfigurations.of(...)` in tests. Boot 4 moved those classes to new packages; a stale name fails startup (`The following classes could not be excluded`) or silently stops excluding, which would start Redis or sessions in the wrong profile. Pinned in Task 4.
4. **JSON field output** (date format, null handling, enum, unknown-property handling, BigDecimal) changes with Jackson 3 defaults and can break the frontend silently. Pinned in Task 3 with a snapshot test of every DTO and of the `GlobalExceptionHandler` error body.
5. **Proxy-header trust** (`server.forward-headers-strategy: native`, `server.tomcat.remoteip.internal-proxies`) on Tomcat 11: client-IP rate limits and audit log depend on it. Pinned in Task 9 with the existing `McpForwardedHeadersConfigTest` plus a spoofed-header case.

---

## File Structure

Modified (no new production classes expected):

| Path | Responsibility in this migration |
|---|---|
| `backend/pom.xml` | parent, starters, Spring AI BOM, Testcontainers 2 artifacts, remove obsolete pins |
| `backend/src/main/resources/application*.yml`, `backend/src/test/resources/application-test.yml`, `application-mcp-test.yml` | property renames, relocated auto-config exclusions |
| `mcp/McpTransportConfiguration.java`, `mcp/BoxPilotMcpTools.java`, `mcp/security/McpAuthorizationService.java`, `mcp/security/McpOAuth2Attributes.java`, `mcp/web/McpClientController.java`, `mcp/authserver/McpAuthenticationNormalizationFilter.java`, `dto/UserPrincipalDTO.java` | Jackson 3 types (`tools.jackson.*`) |
| `config/OAuth2SecurityConfig.java`, `config/RedisConfig.java`, `mcp/authserver/McpAuthorizationServerConfig.java`, `mcp/security/McpSecurityConfig.java`, `mcp/security/McpOAuth2StoreConfiguration.java` | Security 7 / Session 4 API changes |
| `domain/*.java`, `repository/*.java` | Hibernate 7 checks (`@Lob`, `columnDefinition`, JPQL) |
| 22 test classes using Boot test slices, Testcontainers, `ApplicationContextRunner` | relocated test annotations/packages, Testcontainers 2 API |
| `backend/Dockerfile`, `docker-compose.yml`, `.env.example` | containers and runtime config |
| `CLAUDE.md` (root + `backend/`), `README.md:317`, `docs/ops/mcp-server.md` (incl. item 4 at line ~303) | docs |

Not touched: `backend/src/main/resources/db/migration/*` (no edits), `frontend/` (its upgrade is done and separate; only its Docker build is re-verified in Task 11).

---

### Task 0: Baseline on 3.5.16 with the Postgres tests actually running

**Files:** none changed; commit of the existing dependency upgrade.

**Interfaces:**
- Produces: a green, fully executed baseline (`Tests run: N, Skipped: 0`) and a recorded JSON/API snapshot used as the comparison in Tasks 3 and 11.

- [ ] **Step 1: Start Docker Desktop**, confirm `docker version --format '{{.Server.Version}}'` prints a version.
- [ ] **Step 2: Commit the pending 3.5.16 dependency upgrade** (`backend/pom.xml`, frontend files) on `feature/mcp-server`, then branch `feature/spring-boot-4` from it. The pending `application-dev.yml`, `.mcp.json`, `claude-mcp-debug.log` stay out of the commit (not part of this work).
- [ ] **Step 3: Run `mvn clean verify` in `backend/`.** Expected: BUILD SUCCESS, `Skipped: 0`. If the language server breaks `target/classes` as before, close the Java extension's build or run in a copy. This is also the missing check that Flyway 11.7.2 + JDBC 42.7.14 work against real Postgres.
- [ ] **Step 4: Capture the API snapshot.** Start the 3.5 stack with `docker compose up -d --build` against a throwaway DB, seed with a script (`ops`-style `curl` calls: create user via magic link, box, item with image, public share) and save every response body of the endpoints in `controller/*` plus `/api/v1/me`, `/api/v1/csrf`, an error response (invalid body, 401, 403) to `docs/superpowers/plans/artifacts/baseline-3.5/*.json`. Redact tokens. These files are test fixtures for Task 3.
- [ ] **Step 5: Take a `pg_dump` of the dev database and a copy of `pgdata`** for the upgrade rehearsal in Task 11.

### Task 1: Build files - Boot 4.1.2, Spring AI 2.0.2, starters, Testcontainers 2

**Files:** Modify `backend/pom.xml`.

**Interfaces:**
- Produces: a pom that resolves; `mvn dependency:tree` shows Jackson only as `tools.jackson.*` (plus `jackson-annotations` 2.x group, which is expected) and no `spring-boot-starter-classic`.

- [ ] **Step 1: Set parent to `4.1.2`; set `spring-ai.version` to `2.0.2`.** Remove the five security pins from `<properties>` (`jackson-bom`, `tomcat`, `log4j2`, `netty`; keep `postgresql.version` only if the BOM value is lower than 42.7.14, it is equal, so remove it too). Then run the OSV scan from the earlier audit against `mvn dependency:tree` output; re-add a pin only for an artifact that still shows an advisory.
- [ ] **Step 2: Rename starters** as listed in the migration guide:

  | Current | New |
  |---|---|
  | `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
  | `spring-boot-starter-oauth2-client` | `spring-boot-starter-security-oauth2-client` |
  | `spring-boot-starter-oauth2-authorization-server` | `spring-boot-starter-security-oauth2-authorization-server` |
  | `spring-boot-starter-oauth2-resource-server` | `spring-boot-starter-security-oauth2-resource-server` |
  | `org.springframework.session:spring-session-data-redis` | `spring-boot-starter-session-data-redis` |
  | `org.springframework.session:spring-session-jdbc` | `spring-boot-starter-session-jdbc` |
  | `flyway-core` (+ `flyway-database-postgresql`) | `spring-boot-starter-flyway` + keep `flyway-database-postgresql` |
  | `spring-boot-starter-validation` | unchanged name (verify it resolves) |

  Keep `spring-boot-starter-security`, `-data-jpa`, `-data-redis`.
- [ ] **Step 3: Replace test dependencies.** Drop the direct `spring-boot-starter-test`, `spring-security-test`, `spring-boot-testcontainers`; add `spring-boot-starter-webmvc-test`, `spring-boot-starter-security-test`, `spring-boot-starter-data-jpa-test`, `spring-boot-starter-flyway-test`, `spring-boot-starter-session-data-redis-test`, `spring-boot-starter-session-jdbc-test`, `spring-boot-testcontainers`. Testcontainers 2 renamed modules: `org.testcontainers:postgresql` -> `org.testcontainers:testcontainers-postgresql`, `junit-jupiter` -> `testcontainers-junit-jupiter`. Check actual names with `mvn dependency:resolve`. Bump `archunit-junit5` only if it fails on JUnit 6 (needs a release that supports it; check Maven Central).
- [ ] **Step 4: Check the Spring AI starter's own dependency** (`spring-ai-starter-mcp-server-webmvc` 2.0.2 listed `spring-boot-starter-web` in its pom). Run `mvn dependency:tree -Dincludes=org.springframework.boot,io.modelcontextprotocol,org.springframework.ai` and confirm it does not pull Boot 3 era modules or a second Jackson. If it does, exclude and record why in a pom comment.
- [ ] **Step 5: Temporarily add `spring-boot-properties-migrator` (runtime scope)** so Boot reports renamed/removed properties in Task 4. It is removed in Task 4's last step.
- [ ] **Step 6: Run `mvn -q -DskipTests dependency:tree > target/tree.txt`.** Expected: resolves, no conflicts. Compilation is expected to fail until Task 3; that is not a Task 1 failure.
- [ ] **Step 7: Commit** `build(backend): move to Spring Boot 4.1.2 and Spring AI 2.0.2 coordinates`.

### Task 2: OpenRewrite pass for mechanical changes

**Files:** Modify `backend/src/**` (mechanical only).

- [ ] **Step 1: Run the Moderne recipe** `org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0` via `mvn -U org.openrewrite.maven:rewrite-maven-plugin:run -Drewrite.recipeArtifactCoordinates=org.openrewrite.recipe:rewrite-spring:RELEASE -Drewrite.activeRecipes=org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0` (use the `java:openrewrite` skill). Treat its output as a draft: it may also rewrite the pom, so review `git diff` and keep Task 1's decisions where they conflict.
- [ ] **Step 2: Review the diff file by file** and revert any change that touches behaviour you cannot explain. Compile errors left over become the work list for Tasks 3 to 8.
- [ ] **Step 3: Commit** `refactor(backend): apply OpenRewrite Boot 4 recipe`.

### Task 3: Jackson 3

**Files:** Modify the seven main classes in the File Structure table and the eleven MCP test classes that import `com.fasterxml.jackson`. Test: new `dto/JsonContractTest.java`.

**Interfaces:**
- Consumes: baseline JSON files from Task 0 Step 4.
- Produces: `ObjectMapper` -> `tools.jackson.databind.json.JsonMapper`; `JsonNode` -> `tools.jackson.databind.JsonNode`; `JsonProcessingException` -> `tools.jackson.core.JacksonException` (unchecked). `com.fasterxml.jackson.annotation.*` (`@JsonInclude` in `UserPrincipalDTO`) stays.

- [ ] **Step 1: Write `JsonContractTest`** with `@SpringBootTest` + `@AutoConfigureMockMvc` (test profile) that serializes each DTO in `dto/` and the `ErrorResponse` body and compares to the baseline files. Expected before changes: FAIL only where Jackson 3 defaults differ.
- [ ] **Step 2: Migrate imports and call sites.** `McpAuthorizationService` holds `static final ObjectMapper ATTRIBUTE_READER = new ObjectMapper()`: replace with `JsonMapper.builder().build()`; its `catch (JsonProcessingException | NumberFormatException | ArithmeticException e)` becomes `catch (JacksonException | ...)`. `BoxPilotMcpTools` line ~248 `catch (JsonProcessingException e)` same. Remove any custom `ObjectMapper` bean (none found; `McpTransportConfiguration` injects the qualified `mcpServerObjectMapper`, handled in Task 6).
- [ ] **Step 3: Align defaults with 3.5 output.** Where Step 1 shows a diff, either set `spring.jackson.*` (in `application.yml`; note `write-dates-as-timestamps: false` is now `spring.jackson.json.write...` / `spring.jackson.datatype...`; confirm with the properties migrator) or temporarily `spring.jackson.use-jackson2-defaults=true`. The aim is identical JSON to the baseline; record each deviation that is accepted on purpose in the commit message.
- [ ] **Step 4: Run** `mvn -Dtest=JsonContractTest,BoxPilotMcpToolsTest,McpDtoShapeTest test`. Expected: PASS.
- [ ] **Step 5: Commit** `refactor(backend): migrate to Jackson 3`.

### Task 4: Relocated auto-configuration and renamed properties

**Files:** Modify `application.yml`, `application-dev.yml`, `application-prod.yml`, `src/test/resources/application-test.yml`, `application-mcp-test.yml`, and the tests that name auto-configuration classes (`McpFlagOff*Test`, `McpForwardedHeadersConfigTest`, `RedisConfigTest`, `StartupGuardContextTest`, `OAuth2SecurityConfigTest`, others from `grep -rn "boot.autoconfigure"`).

- [ ] **Step 1: List every class name used in config and tests** (`grep -rnE "org\.springframework\.boot\.autoconfigure\.[a-z.]+\.[A-Z]" backend/src`). Known set: `session.SessionAutoConfiguration`, `data.redis.RedisAutoConfiguration`, `data.redis.RedisRepositoriesAutoConfiguration`, `jackson.JacksonAutoConfiguration`, `web.servlet.WebMvcAutoConfiguration`, `web.servlet.DispatcherServletAutoConfiguration`, `security.servlet.SecurityAutoConfiguration`, `http.HttpMessageConvertersAutoConfiguration`. `condition.*` annotations stay. Find each new FQN with `unzip -l` / `jar tf` on the matching `spring-boot-*` module jar or via IDE; do not guess (examples to expect: `org.springframework.boot.session.autoconfigure...`, `org.springframework.boot.data.redis.autoconfigure...`, `org.springframework.boot.webmvc.autoconfigure...`, `org.springframework.boot.jackson.autoconfigure...`).
- [ ] **Step 2: Update the yml exclude lists and test imports.** Rule: a profile that excluded Redis/sessions in 3.5 must exclude the equivalent in 4.1 (`dev`: Redis; `test`: Session; `mcp-test`: Session + Redis x2).
- [ ] **Step 3: Rename properties** found by the migrator report (first start prints them): `spring.session.redis.*` -> `spring.session.data.redis.*` (not used today, verify), `spring.session.store-type` (verify still valid; used in prod/dev/test/main yml), `spring.jpa.database-platform` (H2Dialect/PostgreSQLDialect: Hibernate 7 no longer needs it; remove if the migrator flags it), `spring.ai.mcp.server.*` keys flattened in Spring AI 2 (compare against the Spring AI 2.0 upgrade notes; current keys: `enabled`, `protocol`, `name`, `version`, `streamable-http.mcp-endpoint`).
- [ ] **Step 4: Add a guard test `ProfileExclusionsTest`** using `ApplicationContextRunner` per profile asserting that under `dev` no `RedisConnectionFactory` bean exists and under `prod`-like settings a `SessionRepository` backed by Redis does. Expected: PASS.
- [ ] **Step 5: Boot with each profile** (`dev`: `mvn spring-boot:run -Dspring-boot.run.profiles=dev`) and read the startup log for `Property ... is deprecated/removed` lines. Fix all. Then delete `spring-boot-properties-migrator` from the pom.
- [ ] **Step 6: Run** `mvn -Dtest='RedisConfigTest,StartupGuard*Test,McpFlagOff*Test,McpForwardedHeadersConfigTest' test`. Expected: PASS. Commit `refactor(backend): follow Boot 4 auto-configuration and property renames`.

### Task 5: Spring Security 7 (incl. Authorization Server, Session 4)

**Files:** Modify `config/OAuth2SecurityConfig.java`, `config/RedisConfig.java`, `mcp/authserver/McpAuthorizationServerConfig.java`, `mcp/security/McpSecurityConfig.java`, `mcp/security/McpOAuth2StoreConfiguration.java`, other `mcp/authserver/*` that use SAS internals (converters, validators, token generators, `OAuth2AuthorizationCodeRequestAuthentication*`). Test: existing `OAuth2SecurityConfigTest`, `OidcLoginRejectionTest`, `McpAllowedAdminOnlyTest`, `Mcp*IntegrationTest`; new `McpLegacyAuthorizationRowTest`.

- [ ] **Step 1: Read the Spring Security 7.0 migration guide** for removed deprecated APIs; list those hitting this code with `grep` (DSL `.and()`, `AntPathRequestMatcher`/`MvcRequestMatcher` are not used; `PathPatternRequestMatcher.withDefaults()` already is). Check specifically: `HttpSecurity` lambda DSL only, `AuthorizationFilter` as the authorization filter (class referenced in ordering), `CookieCsrfTokenRepository` + `XorCsrfTokenRequestAttributeHandler` + `DeferredCsrfToken` use, `RequestCacheConfigurer`, `HeadersConfigurer`, `SecurityContextHolderFilter` ordering, `HttpSessionSecurityContextRepository`, `OAuth2AuthorizationServerConfigurer` endpoint customizers, `DefaultOidcUser`/`OidcUserService`, `BCryptPasswordEncoder`, `@EnableMethodSecurity`.
- [ ] **Step 2: Fix compile errors in the chain configs without changing matchers, order or permissions.** Keep `@Order` values; the ordering regression tests are the safety net.
- [ ] **Step 3: Write `McpLegacyAuthorizationRowTest` (Postgres Testcontainer, `McpIntegrationTestBase` pattern).** Fixture: an `oauth2_authorization` row captured from the 3.5.16 build (create it by running the 3.5 `McpEndToEndFlowTest` flow once and dumping the row to `src/test/resources/mcp/legacy-authorization-row.sql`). Assertions: loading it through the production `OAuth2AuthorizationService` bean either returns an authorization with the same subject/scopes/client, or returns `null`/throws a handled exception that makes the resource server answer 401; it never yields an authorization with altered subject or scope and never a 500. Expected before the fix: FAIL or PASS depending on SAS 7 behaviour; document the result in the test's Javadoc.
- [ ] **Step 4: If legacy rows fail to load**, choose and implement one: (a) register the Jackson 2 compatible row mapper SAS 7 ships, if present; (b) add Flyway `V15__invalidate_pre_boot4_oauth2_authorizations.sql` deleting rows, so every Claude connection re-authorizes once. Prefer (a); (b) is acceptable because MCP is off by default and connections are re-creatable. Update the test to assert the chosen behaviour.
- [ ] **Step 5: `RedisConfig`** `@EnableRedisHttpSession` + any serializer config: confirm it is still the supported annotation in Spring Session 4 and that the cookie/serializer behaviour for `same-site: none`, `secure: true` is unchanged (`McpForwardedHeadersConfigTest`, `OAuth2SecurityConfigTest`).
- [ ] **Step 6: Run** `mvn -Dtest='OAuth2SecurityConfigTest,OidcLoginRejectionTest,McpAllowedAdminOnlyTest,McpAuthorizationServerIntegrationTest,McpResourceServerIntegrationTest,McpLegacyAuthorizationRowTest' test`. Expected: PASS. Commit `refactor(security): adapt to Spring Security 7 and Session 4`.

### Task 6: Spring AI 2 / MCP Java SDK 2

**Files:** Modify `mcp/McpTransportConfiguration.java`, `mcp/BoxPilotMcpTools.java`, `mcp/McpTransportContextExtractor.java`, `mcp/McpConfiguration.java`, the tests importing `org.springframework.ai.mcp.server.*`, `io.modelcontextprotocol.*`, `org.springaicommunity.mcp.annotation.*` (`McpAutoConfigClassesTest`-style tests and `McpFlagOff*`).

**Interfaces:**
- Produces: `WebMvcStatelessServerTransport webMvcStatelessServerTransport(JsonMapper, McpServerStreamableHttpProperties)` bean; the transport still carries `McpTransportContextExtractor` so the token subject reaches tools.

- [ ] **Step 1: Resolve the new coordinates and types.** In MCP SDK 2.x `io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper` targets Jackson 2; find the Jackson 3 variant (`io.modelcontextprotocol.json.jackson3.*`) in the `mcp-json-jackson3` jar that Spring AI 2.0.2 pulls, and the new package of `McpTransportContext`, `McpSchema`, `McpStatelessSyncServer`, `WebMvcStatelessServerTransport`. Use `jar tf` on the resolved jars; do not guess.
- [ ] **Step 2: Re-derive the auto-configuration names** used by `McpTransportConfiguration`'s Javadoc claim ("the starter backs off when a transport bean exists") and by the tests (the 10 `org.springframework.ai.mcp.server.*AutoConfiguration` classes). Confirm the back-off condition still exists in 2.0.2 and the qualifier `mcpServerObjectMapper` still exists (it may now be a `JsonMapper` bean with another name). Adjust injection and the test class lists.
- [ ] **Step 3: Check annotation and scanner behaviour:** `@McpTool`, `@McpToolParam`, `McpTool.McpAnnotations` still live in `org.springaicommunity.mcp.annotation`; with `protocol: STATELESS` the tools registered must still be exactly the current set. Existing `McpProtocolTest` (tools/list) covers it; add one assertion that `tools/list` returns the same tool names as the 3.5 baseline snapshot (record from Task 0 run).
- [ ] **Step 4: Confirm the protocol surface:** `/mcp` stays at `streamable-http.mcp-endpoint: /mcp`, `/.well-known/oauth-protected-resource` metadata unchanged (`McpResourceServerIntegrationTest`), and DNS-rebinding protection from SDK 2.x (the CVE-2026-35568 fix) does not reject the legitimate hosts: check what host/origin settings the SDK now offers and set them from `app.mcp.backend-url` if it requires configuration. Add an integration test: request with a foreign `Host`/`Origin` is rejected, request with the configured host passes.
- [ ] **Step 5: Run** `mvn -Dtest='Mcp*Test,BoxPilotMcpTools*Test' test` (Docker on). Expected: PASS. Commit `refactor(mcp): move to Spring AI 2.0 and MCP Java SDK 2`.

### Task 7: JPA, Hibernate 7, Flyway 12

**Files:** Modify `domain/*.java`, `repository/*.java` only if a test shows a need. Test: new `schema/SchemaMatchesFlywayPostgresTest.java`, existing `*PostgresTest`, `McpMigrationTest`.

- [ ] **Step 1: Write `SchemaMatchesFlywayPostgresTest`** (Postgres Testcontainer): run Flyway V1..V14 on an empty DB (as prod does), then boot JPA with `spring.jpa.hibernate.ddl-auto=validate`. Expected: PASS. This catches Hibernate 7 mapping changes (`@Lob String` -> `text`/`oid` mapping on Postgres, `columnDefinition = "boolean not null default false"` on `User.mcpAllowed`, identity columns, enum `STRING` mapping, new `java.time` mappings). Any failure is fixed in the entity mapping, not in an applied migration.
- [ ] **Step 2: Check `Box.description` `@Lob String`.** `DefaultMcpReadService` deliberately filters in memory because of Postgres `@Lob` behaviour; confirm that Hibernate 7 reads/writes the existing column type unchanged using the existing `DefaultMcpReadServicePostgresTest` plus one test that writes via 4.1 and reads the pre-existing dumped row from Task 0 Step 5.
- [ ] **Step 3: HQL/JPQL strictness**: run all repository tests (`@DataJpaTest` ones). Hibernate 7 is stricter about the 5 `@Query` strings (`BoxNumberPoolRepository`, `ItemRepository` projection with `as boxId`, `MagicLoginTokenRepository` bulk update). Fix only if a test fails.
- [ ] **Step 4: Flyway 12:** prod config has `baseline-on-migrate: true`, `validate-on-migrate: false`, `schemas: public`. Rehearse on the Task 0 `pgdata` copy that app start does not re-run or rewrite applied migrations and reports "up to date". Add `flyway-database-postgresql` module check (still required for PG support).
- [ ] **Step 5: H2 dev profile:** H2 2.4 with `ddl-auto: update` and the dev `database-platform` removal; start `dev` against a copy of `data/boxcopilot-dev*` and against an empty DB. H2 file format may need export/import between H2 majors; if the existing dev file does not open, document "delete `data/boxcopilot-dev*`" in `docs`/README rather than adding migration code (dev data only).
- [ ] **Step 6: Run** `mvn -Dtest='*PostgresTest,*H2Test,McpMigrationTest,SchemaMatchesFlywayPostgresTest' test`. Expected: PASS. Commit `test(backend): prove Hibernate 7 and Flyway 12 against Postgres`.

### Task 8: Test infrastructure

**Files:** Modify the 22 test classes using Boot test slices/Testcontainers; `repository/PostgresContainerSupport.java`, `mcp/McpIntegrationTestBase.java`, `test/.../NoSecurityConfig.java`, `controller/testutil/WithMockOidcUser.java`.

- [ ] **Step 1: Slice annotations moved to feature modules.** Current usage: `@SpringBootTest` (10), `@AutoConfigureMockMvc` (7), `@DataJpaTest` (7), `TestEntityManager` (4), `@AutoConfigureTestDatabase` (3), `@TestConfiguration` (2), `ApplicationContextRunner`/`AssertableApplicationContext`/`TestPropertyValues` (stay in `spring-boot-test`), `@MockitoBean`/`@MockitoSpyBean` (already Spring Framework; unchanged). Find the new packages from the test starters' jars (`spring-boot-data-jpa-test`, `spring-boot-webmvc-test`, `spring-boot-jdbc-test`).
- [ ] **Step 2: `@SpringBootTest` no longer provides MockMvc** - the seven `@AutoConfigureMockMvc` classes already declare it; check that `@SpringBootTest` classes using `MockMvc` without it do not exist.
- [ ] **Step 3: Testcontainers 2:** `PostgreSQLContainer` is no longer generic and lives in `org.testcontainers.postgresql`; `@Testcontainers`/`@Container` in `org.testcontainers.junit.jupiter` (artifact `testcontainers-junit-jupiter`); `DynamicPropertySource` usage unchanged. Update `PostgresContainerSupport`, `McpIntegrationTestBase` and the seven `@Testcontainers` classes. Pin the image used in tests to the same major as compose (`postgres:15`), see Task 10.
- [ ] **Step 4: JUnit 6 / Mockito 5.23 / AssertJ 3.27:** run the full unit suite and fix API removals (JUnit 6 drops Java 8 era `@TestInstance` oddities, `junit-vintage`; none expected). Confirm `ArchUnit` `McpArchitectureTest` still runs (ArchUnit bundles ASM reading Java 21 class files).
- [ ] **Step 5: Run the whole suite** `mvn clean verify` with Docker on. Expected: BUILD SUCCESS, `Failures: 0, Errors: 0, Skipped: 0`, test count >= 640 + 207 (Task 0 baseline; new tests add to it). Commit `test(backend): port test infrastructure to Boot 4`.

### Task 9: Web runtime, proxy headers, sessions

**Files:** Modify `application-prod.yml` if needed; tests `McpForwardedHeadersConfigTest`, `GlobalExceptionHandlerTest`, controller tests; new `RedisSessionCompatibilityTest`.

- [ ] **Step 1: Tomcat 11 / Servlet 6.1:** `server.tomcat.remoteip.internal-proxies` and `server.forward-headers-strategy: native` are unchanged in Boot 4; verify with the properties metadata (the migrator is gone, so check `spring-boot-configuration-metadata` or start the app in prod-like profile with the `McpForwardedHeadersConfigTest`). Add a case: `X-Forwarded-For` from a peer outside `internal-proxies` is ignored (client IP = peer).
- [ ] **Step 2: Multipart limits** (`spring.servlet.multipart.max-file-size: 15MB`) unchanged and enforced: add/keep a test that a 16 MB upload to `ItemController.uploadImage` returns the handled error, and that the body of the error response matches the baseline from Task 0.
- [ ] **Step 3: `GlobalExceptionHandler`:** `HttpMessageNotReadableException` / `MethodArgumentNotValidException` messages and status codes must match baseline. Spring Framework 7 changed default handling in some exceptions (`NoResourceFoundException`, `HandlerMethodValidationException`); run `GlobalExceptionHandlerTest` plus a request with malformed JSON and compare to the baseline artifact.
- [ ] **Step 4: Redis sessions:** `RedisSessionCompatibilityTest` (Redis Testcontainer): write a session entry in the 3.5 format (captured fixture hash from a 3.5 run, `spring:session:sessions:*`), read through the 4.1 `SessionRepository`. Expected and acceptable: returns `null` (user logs in again) - never an exception that returns HTTP 500 for every request carrying that cookie. If it throws, add handling or flush instruction to the release notes in Task 12.
- [ ] **Step 5: Run** the tests above. Commit `test(backend): lock proxy headers, upload limits, error bodies and session compatibility`.

### Task 10: Dockerfile, docker-compose, .env

**Files:** Modify `backend/Dockerfile`, `docker-compose.yml`, `.env.example` (and `.env`/`.env.dev` only locally, never committed).

- [ ] **Step 1: `backend/Dockerfile`:**
  - Build stage `maven:3.9-eclipse-temurin-21` -> pinned minor (`maven:3.9.12-eclipse-temurin-21`; Maven 3.9.12 is what is installed locally and works), runtime `eclipse-temurin:21-jre` -> pinned patch tag (`eclipse-temurin:21.0.12_7-jre`, check the tag exists) or digest.
  - Copy `pom.xml` first and run `mvn -B -q dependency:go-offline` before copying `src` so dependency layers cache.
  - Run as a non-root user owning `/app/logs` and `/var/boxcopilot/images`; keep `EXPOSE 8080`.
  - Add `ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"`.
  - Add `HEALTHCHECK` using bash TCP (`bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080'`; the JRE image has bash, no curl). Reason: no actuator dependency is added (new endpoint = new attack surface).
  - Keep the ENTRYPOINT jar; the JAR name stays `backend-0.1.0.jar`.
- [ ] **Step 2: `docker-compose.yml`:**
  - Remove the obsolete `version: '3.8'` key.
  - Remove unused named volumes (`backend-logs`, `boxcopilot-images`; the compose uses bind mounts `./logs`, `./images`, `./pgdata`). Check with `docker volume ls` before deleting anything; do not touch data.
  - `backend.depends_on` stays with `service_healthy` for postgres/redis; add `healthcheck` for backend (same TCP check) and make `frontend.depends_on.backend.condition: service_healthy`.
  - `postgres:15`: keep (Hibernate 7.4 and Flyway 12 support it; a major PG upgrade on `./pgdata` is out of scope). Pin to a patch tag (`postgres:15.x-alpine`? changing alpine vs debian changes collation: keep debian, only pin the patch).
  - Env: compose passes `SPRING_DATA_REDIS_HOST/PORT`, but `application-prod.yml` reads `${REDIS_HOST}` and `${REDIS_PORT}` from `.env`. Confirm which wins in Boot 4 relaxed binding and make the two consistent (single source), so no env var is silently ignored.
  - Env var names that map to renamed properties: grep `.env*`, compose and docs for `SPRING_SESSION_*`, `SPRING_JACKSON_*`, `SPRING_AI_MCP_*`; rename any that hit Task 4/6 renames.
  - Traefik labels, networks and the `traefik-proxy` external network are unchanged.
- [ ] **Step 3: `.env.example`**: add nothing new unless Tasks 5/6 introduce a required setting; if the MCP DNS-rebinding allow-list needs an env var, document it next to `BACKEND_URL`.
- [ ] **Step 4: Verify** `docker compose config` (valid), `docker compose build backend frontend` (frontend uses `nginx:1.30-alpine`; this is also the first real build check for that image and for `nginx.conf`, add `RUN nginx -t` only if a failure shows up).
- [ ] **Step 5: Commit** `build(docker): pin images, add healthchecks and non-root runtime for Boot 4`.

### Task 11: Full functional verification (the user's "check everything")

**Files:** none (evidence in `docs/superpowers/plans/artifacts/verification-boot4.md`).

- [ ] **Step 1: Unit/integration:** `mvn clean verify` in `backend/` (Docker on): 0 failures, 0 skipped. `npm test` + `npm run build` + `npx tsc --noEmit` in `frontend/`.
- [ ] **Step 2: Compose stack on a copy of production-like data:** restore the Task 0 `pgdata`/dump into the stack, `docker compose up -d --build`. Expected: backend `healthy`, no `ERROR` and no `Jackson 2`/deprecation warnings in `logs/boxcopilot.log`, Flyway "Schema is up to date".
- [ ] **Step 3: Functional matrix via the `e2e-testing` skill** (Playwright, mocked Nextcloud OAuth): username/password login, OIDC login incl. rejection path, magic link create/redeem/expire, logout and Nextcloud logout redirect, CSRF cookie flow, admin panel (users, MCP toggle, magic links), boxes CRUD + number pool, items CRUD, image upload/serve/thumbnail/token URL, move item, label PDF generation, public share/preview (valid, invalid, revoked token), language switch, 401/403/validation error bodies. Compare every response body against the Task 0 baseline.
- [ ] **Step 4: MCP end to end** with `APP_MCP_ENABLED=true`: discovery documents, dynamic/pre-provisioned client, authorize + consent + token (PKCE), `tools/list`, each tool call, image tool, revoked user, expired connection, rate limits, audit log lines, and with `APP_MCP_ENABLED=false` that `/mcp` and the OAuth endpoints are unreachable. Use MCP Inspector (steps in `docs/ops/mcp-server.md`) and the existing `McpEndToEndFlowTest` as reference.
- [ ] **Step 5: Upgrade rehearsal:** start the 3.5.16 image on the data copy, create a session, a magic link and an MCP authorization; stop; start the 4.1 image on the same Postgres and Redis. Record: sessions (expected: re-login), MCP connection (Task 5 result), data intact.
- [ ] **Step 6: Security re-scan:** run the osv.dev scan on the resolved runtime classpath (same method as the earlier audit) and `npm audit` for `frontend/`. Expected: no advisory left for Spring Framework, MCP SDK, Jackson, Tomcat, Netty, log4j; anything remaining is listed with reason.
- [ ] **Step 7: Run `graphify update .`** and commit evidence.

### Task 12: Documentation and release notes

**Files:** Modify `CLAUDE.md` (root, if it mentions versions), `backend/CLAUDE.md` ("Spring Boot 3.5"), `README.md:317` ("Spring Boot 3.5"), `docs/ops/mcp-server.md` (decision 4 at ~line 303 about Boot 3.5 support; pinned image notes), `config/CLAUDE.md`/`mcp/CLAUDE.md` if class names are cited.

- [ ] **Step 1: Update version statements** and resolve decision 4 in `docs/ops/mcp-server.md` (Boot 3.5 OSS support ended 2026-06-30; now on 4.1, OSS until 2027-07-31).
- [ ] **Step 2: Write "Upgrading from 3.5" operations notes** in `docs/ops/mcp-server.md` or README: users must log in again once (Redis sessions), MCP connections may need re-authorizing (per Task 5), take a `pg_dump` first, rollback = redeploy previous image (no schema change, unless V15 was added; if it was, rollback also restores the dump), dev H2 file may need deleting.
- [ ] **Step 3: Commit** `docs: describe Spring Boot 4.1 stack and upgrade notes`, then use `superpowers:finishing-a-development-branch`.

---

## Self-Review

- **Coverage:** build files (1), mechanical pass (2), Jackson (3), auto-config + properties (4), Security/Session/SAS (5), Spring AI/MCP (6), Hibernate/Flyway/H2 (7), tests (8), Tomcat/proxy/multipart/errors/Redis (9), Docker/compose/env (10), full functional + MCP + upgrade rehearsal + CVE rescan (11), docs (12), baseline (0). Frontend untouched except its Docker build (Task 10 Step 4, Task 11).
- **Known unknowns that tasks resolve by inspection, not by guessing:** exact new package names of relocated Boot classes (Task 4/8), Jackson 3 MCP mapper class and bean names (Task 6), SAS 7 reading of old JDBC rows (Task 5), Hibernate 7 mapping of `@Lob String` on Postgres (Task 7), Testcontainers 2 artifact/class names (Tasks 1/8), whether `archunit-junit5` needs a bump (Task 1).
- **Decisions needing the owner (defaults chosen):** (a) target Boot 4.1.2, not 4.0.9; (b) stay on Java 21 (Boot 4.1 also supports 25 LTS; separate change); (c) no actuator, TCP healthcheck instead; (d) accept one forced re-login and possible MCP re-authorization on deploy; (e) Postgres stays on 15.
- **Order risk:** Tasks 3 to 8 leave the build red until the end of Task 8 on the full suite; each task's narrow tests are green at its own end, and compilation is green from Task 6 on. Do not reorder Tasks 3 and 4 (the properties migrator output feeds Task 3 Step 3).
