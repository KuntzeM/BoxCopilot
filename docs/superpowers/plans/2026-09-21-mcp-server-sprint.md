# MCP-Server – Sprint Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Each story = one fresh senior subagent (TDD, own commit) followed by two-stage review (Security + Architecture/Code Quality). Steps use checkbox syntax.

**Goal:** Read-only MCP server in the Spring Boot backend (4 tools), protected by built-in OAuth 2.1 (Spring Authorization Server, opaque tokens, per-user client binding), plus SPA support and hardening.

**Architecture:** Package `com.boxcopilot.backend.mcp`, three ordered `SecurityFilterChain`s (AS / Resource Server / existing app chain), `McpAuthorizationService` decorator + `McpAccessPolicy` (fail-closed, no cache), stateless MCP transport (Spring AI 1.1.x). Everything behind `app.mcp.enabled=false` default.

**Tech Stack:** Java 21, Spring Boot 3.5.0, Spring Security 6.5 / SAS 1.5, Spring AI 1.1.x MCP starter (webmvc), Flyway, Postgres (Testcontainers in tests), React/TS/MUI, ArchUnit, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-21-mcp-server-design-v4.md` (authoritative; section refs below = spec sections)

## Global Constraints

- Feature flag `app.mcp.enabled` default `false`; off = no MCP beans, `/mcp` + `/oauth2/authorize|token` give no 2xx and no data; `spring.ai.mcp.server.enabled=${app.mcp.enabled:false}` (§3.1)
- Tools read-only, limit max 50, query max 100 chars, empty query rejected; DTOs never contain `imageToken`, `imageUrl`, Box-UUID (§3.2, §3.3)
- Tokens: opaque REFERENCE, access 30 min, refresh rotation `reuseRefreshTokens(false)`, idle 7 d, absolute `app.mcp.max-connection-age` 14 d via attribute `boxpilot.started_at` (Long) (§4.1)
- Client-ID: ≥128 bit `SecureRandom` Base64url, bound to userId in `client_settings` `boxpilot.user_id`; **never logged** (§2.1)
- Access control fail-closed, fresh DB load per call, any error = deny (§3.1)
- Temporary lock (`lockedUntil`) denies but deletes nothing; disable / delete / revoke `mcp_allowed` deletes client + authorizations (§2.1)
- Principal name in token = internal userId; only authority `SCOPE_mcp:read` (§4.3)
- MCP tests use profile `mcp-test` with real chains, never `NoSecurityConfig`; DB tests via Testcontainers-Postgres (§3.1, §6)
- Package rule (ArchUnit): only `mcp` uses `mcp`; `mcp` may use services, not controllers (§2)
- Senior standards: constructor injection, immutability/records for DTOs, no field injection, interfaces at boundaries, small focused classes, no commented-out code, no TODOs, TDD (red→green), conventional commits
- Code comments/commits/docs in English; German only in UI strings already German-localized (use existing `useTranslation`)
- graphify: run `graphify query "<q>"` (`C:/Users/mathi/.local/bin/graphify.EXE`) before grepping source; run `graphify update .` after code changes

## Team & Roles

| Role | Agent | Owns |
|---|---|---|
| Product Owner / Scrum Master / Tech Lead | main session | backlog, story acceptance, integration, conflicts |
| BE-Sec (Sr Backend, security) | subagent | S1, S2, S3-BE, S7, S8, S9 |
| BE-Data (Sr Backend, data/tools) | subagent | S4, S5, S6-BE, S10 |
| FE (Sr Frontend) | subagent | S3-FE, S6-FE, S11 |
| QA (Sr Test) | subagent | S12 (integration/E2E/ArchUnit) + test-gap review per wave |
| Reviewer-Sec | subagent | security review per story (spec §2.1, §4.3) |
| Reviewer-Arch | subagent | patterns, boundaries, code quality per story |

## Backlog, Estimates, Order

Points = Fibonacci (relative complexity). Total 84 pts. Working tree is shared → backend stories run sequentially; FE stories run in parallel to backend only when files are disjoint.

| # | Story | Pts | Depends | Spec |
|---|---|---|---|---|
| S1 | Test-config isolation + start guards | 3 | – | 3.1, 4.4-3 |
| S2 | Baseline security fixes (OIDC state check, generic errors, show-sql, admin pw guard, chain hygiene, Redis pw/pin, CSRF login) | 8 | S1 | 4.4 |
| S3 | Magic-link rework (BE atomic/POST/CSRF; FE `/magic` route + referrer policy) | 8 | S2 | 4.4-1 |
| S4 | Read data layer: repos, DTOs, sanitizer, `McpReadService` | 5 | S1 | 3.3 |
| S5 | Flag + MCP transport + 4 tools + context | 8 | S4 | 3.1, 3.2, 3.4 |
| S6 | `mcp_allowed` migration V13 + DTOs + Admin toggle (BE+FE) | 3 | S2 | 2.1, Phase 3 |
| S7 | Resource server: SAS tables V14, decorator, policy, introspector, chain, 401 header, metadata | 13 | S5, S6 | 3.1, 4.1, 4.2 |
| S8 | Authorization server: clients, validator, normalization filter, consent, cookie-pending, continue, `mcpPending` | 13 | S7 | 2.1, 4.1, 4.3 |
| S9 | Client provisioning API + revoke + lifecycle deletion | 5 | S8 | 2.1, 4.3 |
| S10 | Hardening: rate limits, audit, cleanup, Micrometer, forwarded headers (M4) | 8 | S9 | 4.3, 4.4 |
| S11 | SPA: “Claude-Verbindung” dialog, `mcpPending` redirect | 5 | S9 API contract | 2.1, 4.1 |
| S12 | QA: cross-cutting integration suite, ArchUnit, Postgres migration test, E2E script | 5 | S10 | 6 |
| S13 | Docs/ops: README, ops guide, compose/Traefik notes, open-points list | 3 | S10 | 5 |

Waves: **W1** S1→S2 · **W2** S3-BE ∥ S3-FE, then S4 · **W3** S5, S6 (BE then FE) · **W4** S7→S8→S9 · **W5** S10 ∥ S11 · **W6** S12, S13, final full-branch review.

## Definition of Ready / Done

- Ready: story has acceptance criteria below, interfaces named, dependencies merged.
- Done: failing test first, all tests green (`mvn -q test` in `backend`, `npm run build` + tests in `frontend`), no new warnings, story review (Sec + Arch) findings fixed or explicitly deferred with reason, one commit per story on `feature/mcp-server`, `graphify update .` run.

## Stories

### S1 – Test-config isolation + start guards (3 pts)
AC:
- `NoSecurityConfig` and `application-test.yml` live in `backend/src/test` (java + resources); existing tests still pass.
- Startup fails when profile `test` is active together with `prod` (or Postgres datasource) — `StartupGuard` (config package), unit-tested.
- Test: `StartupGuardTest`.

### S2 – Baseline security fixes (8 pts)
AC (each with test):
- `UserService.createOrUpdateOidcUser` rejects disabled/locked users (`UserService.java:256-267`).
- `GlobalExceptionHandler` returns generic message, details only in log (`:36`, `:115`).
- `spring.jpa.show-sql=false`.
- Prod: `ADMIN_PASSWORD` has no default; start fails if missing or equals `admin`; one-time startup check that no ADMIN hash matches `admin` (`passwordEncoder.matches`) → fail/log per spec.
- `OAuth2SecurityConfig`: `permitAll` narrowed from `/oauth2/**` to `/oauth2/authorization/**`; add `@Order(3)` to the chain.
- CSRF exceptions for `/api/auth/login` and `/api/v1/auth/magic-login` removed; FE `LoginPage` already fetches `/api/v1/csrf` (verify).
- `docker-compose.yml`: Redis `requirepass` (`REDIS_PASSWORD`), pinned image tag; `spring.data.redis.password` wired.

### S3 – Magic-link rework (8 pts)
BE AC:
- Link format `<FRONTEND_URL>/magic#t=<token>`; old GET endpoint removed; `POST /api/v1/auth/magic-login` body `{ "token": "..." }`, CSRF required.
- Atomic consume: `UPDATE … SET used=true WHERE token=? AND used=false AND expiry > now()`, rows==1 → success (parallel double-use test).
- Checks `enabled` + `lockedUntil`; `changeSessionId`; security context saved via `SecurityContextRepository`.
- `expiresInHours` max 72; no token prefix in any log; `resolveBaseUrl` fail-fast on empty `FRONTEND_URL`.
FE AC:
- Route `/magic`: read fragment, `history.replaceState` to strip, GET `/api/v1/csrf`, POST, navigate `/`. Error state shown. nginx + meta `Referrer-Policy: no-referrer`.

### S4 – Read data layer (5 pts)
Produces (package `mcp`, DTO records):
```java
record McpItemHit(long itemId, String name, int boxNumber, List<String> rooms, boolean hasImage)
record McpBoxHit(int boxNumber, List<String> rooms, List<String> flags, long itemCount)
record McpBoxContents(McpBoxHit box, String description, List<McpItemRef> items)
record McpItemRef(long itemId, String name, boolean hasImage)
interface McpReadService {
  List<McpItemHit> searchItems(String query, int limit);
  List<McpBoxHit> searchBoxes(String query, String room, int limit);
  Optional<McpBoxContents> boxContents(int boxNumber);
  Optional<McpItemImage> itemImage(long itemId, ImageSize size);   // bytes + name + boxNumber
}
final class McpTextSanitizer { static String clean(String s, int maxLen) }
```
AC: `Pageable` search methods in `ItemRepository`/`BoxRepository` + `findByBoxNumber`, fetch-join (no N+1), `itemCount` via count query; LIKE `%`/`_` treated literally (test with real DB); description ≤500 chars; sanitizer strips control/zero-width/bidi chars; DTOs contain no token/UUID; image errors generic. Existing `ItemService.searchItems` untouched.
(Verify actual `Box`/`Item` field names with graphify before coding; adapt DTO field types to the entity — e.g. box number type.)

### S5 – Flag, transport, tools (8 pts)
AC:
- Deps added (Spring AI BOM 1.1.x, `spring-ai-starter-mcp-server-webmvc`), `spring.ai.mcp.server.protocol=STATELESS`, endpoint `/mcp`, `spring.ai.mcp.server.enabled=${app.mcp.enabled:false}`.
- `BoxPilotMcpTools` with `@McpTool` `search_items`, `get_item_image` (`ImageContent` base64 JPEG, `thumbnail` default), `search_boxes`, `get_box_contents`; `readOnlyHint`; input validation (limit 1..50, query 1..100); tool descriptions mention untrusted data.
- `McpTransportContextExtractor` puts `userId` in context; each tool calls `McpAccessPolicy.requireActiveUser(context)` (interface defined here, real impl in S7; use a policy port `McpUserGate`).
- Flag off → no MCP beans (`ApplicationContextRunner` test); tool call without context fails closed; generic tool errors.
- Startup guards: `app.mcp.enabled=true` needs `BACKEND_URL` (https in prod), ≥1 redirect URI, profile ≠ `test`.

### S6 – `mcp_allowed` (3 pts)
AC: Flyway `V13__add_users_mcp_allowed.sql` (`BOOLEAN NOT NULL DEFAULT FALSE`); `User.mcpAllowed`, `UserDTO.mcpAllowed`, `UpdateUserDTO.mcpAllowed` (only settable via admin endpoints); AdminPanel switch; revocation hook publishes `McpAccessRevokedEvent(userId)` (consumed in S9).

### S7 – Resource server (13 pts)
AC:
- Flyway `V14` SAS tables `oauth2_registered_client`, `oauth2_authorization` with `text`/`bytea`.
- `McpAccessPolicy` (`checkAuthorization(OAuth2Authorization)`, `requireActiveUser(userId)`), fail-closed; `McpAuthorizationService` decorator (stamps `boxpilot.started_at`, policy on all reads); `McpOpaqueTokenIntrospector` (ACCESS_TOKEN only; refresh as bearer → 401; authority `SCOPE_mcp:read`).
- `McpSecurityConfig`: `@Order(2)` chain `/mcp`, `/mcp/**`, `/.well-known/oauth-protected-resource/**`; stateless, opaque, no cookies/CORS/CSRF; 401 entry point with `WWW-Authenticate: Bearer resource_metadata="…", scope="mcp:read"`; missing scope → 403; PRM static JSON.
- Tests (`mcp-test`, Postgres Testcontainers): 401 header, refresh-as-bearer 401, decorator all paths, absolute age, lock-vs-delete, Bearer on `/api/**` ineffective, session on `/mcp` ineffective.

### S8 – Authorization server (13 pts)
AC:
- `McpAuthorizationServerConfig`: fixed issuer `BACKEND_URL`, REFERENCE tokens, TTLs, JDBC stores, No-Op consent service, `securityMatcher` limited to `/oauth2/authorize|consent|token|revoke` + `/.well-known/oauth-authorization-server`, `@Order(1)`, PKCE S256 only, exact redirect match, code TTL 60 s, `reuseRefreshTokens(false)`, default-scope converter. Works without `JWKSource` or with unused in-memory key.
- Authorize validator: client-binding check only for authenticated principal; anonymous → entry point.
- Entry point sets cookie `mcp_authz` (base64url query, ≤2 KB else 400, HttpOnly, Secure, Path=/, Max-Age=600, SameSite configurable) and redirects to `FRONTEND_URL`; no session created.
- `McpAuthenticationNormalizationFilter`: swap for `UsernamePasswordAuthenticationToken(User(userId))`, fresh DB load + `mcp_allowed`; SPA session untouched.
- `McpPendingAuthorizationController`: `GET /api/v1/mcp/continue` (auth required, consume cookie, ASCII/`#` check, redirect only to `BACKEND_URL/oauth2/authorize?<q>`); `/api/v1/me` gains `mcpPending` (authenticated only).
- Consent page: server-rendered HTML, escaped, username + scope + data-transfer notice + warning, CSRF token, decline button, headers CSP `default-src 'none'; form-action 'self'; frame-ancestors 'none'`, `X-Frame-Options: DENY`; consent required every time.
- Tests per spec §6 (Consent, PKCE/redirect, principal swap, issuer vs `X-Forwarded-Host`, cookie-pending, chains, unneeded SAS endpoints unreachable, no sessions from anonymous flood).

### S9 – Client provisioning + lifecycle (5 pts)
Produces REST contract (session + CSRF, only if `mcp_allowed`, flag on):
- `POST /api/v1/me/mcp-client` → `200 {"clientId":"…"}` idempotent, lazy.
- `POST /api/v1/me/mcp-client/regenerate` → `200 {"clientId":"…"}`; old client + authorizations gone.
- `DELETE /api/v1/me/mcp-connections` → `204`.
- `McpClientService`; `deleteByPrincipal(userId)` on JDBC store; listeners for disable/delete/`mcp_allowed` revoke (from S6 event + `UserService` hooks). Lock does not delete. Client-ID absent from logs (test with log capture).

### S10 – Hardening (8 pts)
AC: `McpRateLimiter` (per `sub` 60/min, 200 image calls/day; IP 30/min on authorize/token; failed token lookups per IP 30/min; configurable, `Clock`-injected for tests); `McpAuditLogger` (sub, tool, hit count, IP; never token/client-id); Micrometer counters `ok|denied|rate_limited|error` per tool; `McpAuthorizationCleanup` `@Scheduled` daily; M4: `forward-headers-strategy: native` + `server.tomcat.remoteip.internal-proxies`; debug logging off in prod.

### S11 – SPA (5 pts)
AC: after login, if `/api/v1/me` returns `mcpPending=true` → `window.location.assign(<backend>/api/v1/mcp/continue)`; “Claude-Verbindung” dialog in the menu (`App.tsx`): show/copy client-ID, “Neu erzeugen” (confirm), “Alle Verbindungen widerrufen” (confirm), hidden unless user `mcpAllowed`; services in `frontend/src/services`, types in `types/models.ts`; MUI patterns and `useTranslation` as elsewhere; component tests where infra exists.

### S12 – QA integration (5 pts)
AC: full §6 matrix gaps closed (QA reviews all stories’ tests, adds missing); ArchUnit `McpArchitectureTest`; Postgres migration + Jackson-allowlist test; scripted end-to-end flow test (authorize→login→consent→token→refresh→`/mcp` tools/list + tools/call) via MockMvc/`TestRestTemplate`; MCP Inspector manual steps documented.

### S13 – Docs/ops (3 pts)
AC: README MCP section; ops guide (client regeneration, revoke all, kill-switch, cleanup, access-log confidentiality, DB backup encryption); compose/Traefik notes; env var table; “Open points” list: Claude Free manual spike (§7 Phase 0.1/0.5), Boot 3.5 support-status decision, staging `flyway validate`, remaining §4.4 tickets (M3 etc.).

## Out of scope this sprint (documented as open)
Manual Claude Free/Mobile verification, ChatGPT check, Traefik runtime config, staging `flyway validate`, Boot 4 upgrade, M3 session invalidation, container hardening, login-message unification, `validate-on-migrate` global switch.

## Spec Coverage Self-Check
§2.1→S8/S9 · §3.1→S5/S7/S8/S10 · §3.2→S5 · §3.3→S4 · §3.4→S5 · §4.1→S7/S8 · §4.2→S2/S7/S8 · §4.3→S7–S10 · §4.4→S1–S3/S10 · §5→S13 · §6→per story+S12 · §7 Phase 0 spike points → encoded as tests in S7/S8/S4 (points needing external systems → open list).
