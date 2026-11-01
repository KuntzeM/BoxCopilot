# Spring Boot 4.1 verification evidence (Task 11a, backend part)

Branch `feature/spring-boot-4`, verified at `03b647c` plus the fix `a41fd9c` (see "Defects"). Everything ran on
Windows 11 / Docker Desktop 29.9.2, Temurin 21.0.12.1, Maven 3.9.12, Node 24.22.1, Python 3.13. All containers,
passwords and tokens were throwaway (`bp-throwaway`, `redis-throwaway`, the baseline's admin password); nothing
here is a credential. Builds and runs happened outside the repository (`<outside-repo>/BoxPilot-build4/`).

## Summary

| Step | Result |
|---|---|
| 1 Build/tests | backend `mvn clean verify`: 706 tests, 0 failures, 0 errors, 0 skipped (705 before the fix's new test); frontend 45 tests green, build, `tsc`, `npm audit` 0 vulnerabilities |
| 2 API snapshot diff | 96 files: 92 identical, 4 expected diffs, 0 unexpected after fix `a41fd9c` (before it: 91 differed unexpectedly) |
| 3 Compose on baseline data | all services healthy, Flyway "up to date" at v14, `flyway_schema_history` byte-identical (md5), 0 ERROR, no Jackson 2 / deprecation warnings, data + image readable |
| 4 Upgrade 3.5 -> 4.1 | 3.5 session cookie still signed in, magic link redeemable, 3.5 MCP access + refresh token accepted, data and images intact; no 500 |
| 5 Rollback 4.1 -> 3.5 | data (17 kB unicode LO description), items, users, images, MCP authorization written by 4.1 all readable by 3.5; **Redis sessions written by 4.1 make 3.5 answer 500 on every request carrying that cookie** (ClassNotFoundException `FactorGrantedAuthority`); flushing `spring:session:*` turns them into clean anonymous/401 |
| 6 CVE rescan | osv.dev: 165 runtime artifacts, 0 advisories (control run on the 3.5 set reproduces the 3 known ones); `npm audit` 0; docker scout: backend image 0 vulnerable packages, frontend `nginx:1.30-alpine` 8C/18H in OS packages (curl, brotli, libxml2), postgres/redis upstream only |

## Step 1: unit/integration

```
cd backend && mvn -B clean verify          # copy of backend/ + docs/ outside the repo
[INFO] Tests run: 706, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

74 test classes, Docker on (Testcontainers Postgres tests ran). Before the fix: 705 / 0 / 0 / 0.

Note: a first run in a copy holding only `backend/` failed 13 tests (`JsonContractTest`,
`McpResourceServerIntegrationTest.toolsListMatchesTheSpringBoot35Baseline`, `RedisSessionCompatibilityTest`)
with `NoSuchFileException ..\docs\superpowers\plans\artifacts\baseline-3.5\...`: those tests read the baseline
fixtures from the repository's `docs/` tree, so the module must be built inside the repo layout (or with `docs/`
copied next to it). Not a product defect.

Frontend (`frontend/`): `npm ci` ok; `npm test` -> `Test Files 8 passed (8)`, `Tests 45 passed (45)`;
`npm run build` -> built in 2.75 s; `npx tsc --noEmit` exit 0; `npm audit` -> `found 0 vulnerabilities`.

## Step 2: API snapshot diff against baseline-3.5

Setup exactly as `baseline-3.5/README.md` step 4: 4.1 jar from step 1, profile `dev` on `postgres:15.19`
(empty database), Redis `7.4.11-alpine`, static OIDC discovery on `localhost:9999`, MCP enabled, same env,
same `snapshot.py` (unchanged), then a per-file comparison of the parsed JSON (`diffsnap.py`: `json.load` both,
compare, unified diff of the normalised dump).

| File | Status | Explanation |
|---|---|---|
| 01-10, 12-84, 86-88, 90-94, 96 (92 files) | identical | |
| 11-admin-login | expected-diff | cleared `XSRF-TOKEN` cookie carries `Expires=Thu, 01 Jan 1970 00:00:10 GMT` without `Max-Age=0` (Tomcat 11 cookie processor). Browsers still delete it. |
| 95-logout | expected-diff | same as 11 for `JSESSIONID` and `XSRF-TOKEN`; `SESSION` keeps `Max-Age=0` |
| 85-mcp-tools-list | expected-diff | each `inputSchema` gains `"$schema": "https://json-schema.org/draft/2020-12/schema"` (Spring AI 2 schema generator); tool names identical |
| 89-mcp-call-get-item-image | expected-diff | key order inside the tool's text JSON: `{"boxNumber":1,"name":"Reading lamp"}` instead of `{"name":...,"boxNumber":1}`. Jackson 3 sorts properties alphabetically by default (`MapperFeature.SORT_PROPERTIES_ALPHABETICALLY`); REST bodies are alphabetical too (e.g. `/api/v1/me`). JSON object order carries no meaning; the frontend reads by key. |
| 91-mcp-err-call-invalid-arguments | **unexpected-diff, fixed** | 4.1 answered `Tool (search_items) input validation failed: ... [: erforderliche Eigenschaft 'query' nicht gefunden]` (MCP SDK 2 schema validation, JVM locale) instead of `Invalid input. Check the parameters and their allowed ranges.`, and the call was neither audited nor counted. Fixed in `a41fd9c`; re-run identical. |
| 92-mcp-token-refresh | identical on the re-run | first run had `expires_in: 1799` vs 1800 (sub-second rounding of the same 30 min TTL); timing only |

`mcp-tool-names.txt` identical in content (only line endings differ from the checked-out file).

WARN/ERROR lines of that run (grouped): all ERROR lines (10) are the provoked error cases of the script and match
the 3.5 baseline behaviour (unknown box/item ids, duplicate username, missing `q`, non-image upload, 16 MB upload,
missing file part). WARN: `GlobalExceptionHandler` validation (provoked), Flyway `already exists, skipping`
(V-scripts with IF NOT EXISTS, same in 3.5), Spring AI `No resource/prompt/complete methods found` and four
`BeanPostProcessorChecker` lines (same four in 3.5), one `HHH90000025 PostgreSQLDialect does not need to be
specified` caused by the baseline's own `SPRING_JPA_DATABASE_PLATFORM` env var (not set in compose).

## Step 3: compose stack on the baseline data

`docker compose -p bp-sb4-verify -f docker-compose.yml -f <override>` with a temporary `.env` (throwaway values,
profile `prod`, MCP off, `SERVER_SERVLET_SESSION_COOKIE_SECURE=false` for plain http) and an override that
renames the containers (`bp-sb4-verify-*`), puts Postgres on a project volume, logs/images on scratch dirs,
replaces the external `traefik-proxy` network by a throwaway one, publishes the backend on `127.0.0.1:18080` and
adds a python OIDC discovery stub (`http://oidc:9999`) on the compose network. Images built from the worktree.

- `boxpilot-3.5.sql` restored into the compose Postgres (`postgres:15.19`) before the backend started.
- `ps`: postgres healthy, redis healthy, backend healthy, frontend up (serves `index.html`; reaches
  `backend:8080`).
- Log: `Schema "public" is up to date. No migration necessary.`, started in 6.9 s, profile `prod`.
- `flyway_schema_history`: 13 rows, latest version 14; md5 over `installed_rank|version|checksum|installed_on|success`
  `45807302f4b42e9cf30ace999f171864` before and after startup.
- `logs/boxcopilot.log`: 0 ERROR (`boxcopilot-error.log` empty), 0 lines matching `jackson 2|com.fasterxml|deprecat`.
  WARN lines: only the four Spring AI/Flyway `BeanPostProcessorChecker` lines, identical to the 3.5 prod run of
  Task 0.
- API: admin login 200 (cookie `SESSION`, Redis), `/me` admin, boxes 1 `Cellar` "Winter things (updated)" with
  `Reading lamp`, 2 `Attic` "Decorations" with `Candle`, users `snapuser`, `admin`; `Reading lamp` image 200
  `image/jpeg` 3700 bytes from the restored image store; box-number pool `{"totalNumbers":3,"availableNumbers":1,...}`;
  `snapuser` login 200, new box with 9000 x `ä` description 201 and read back equal; 2 Redis session keys.

## Step 4: upgrade rehearsal 3.5.16 -> 4.1.2

Both versions as jars on the host against the same Postgres (`boxpilot-3.5.sql` restored into db `rehearsal`)
and the same Redis, profile `prod` (Redis sessions, `RedisConfig`), MCP on, plain http
(`SERVER_SERVLET_SESSION_COOKIE_SECURE=false`, `SAME_SITE=lax`, `APP_MCP_BACKEND_URL=https://localhost:8080`
to satisfy the prod https guard; the flow itself ran over http on localhost). 3.5 = the Task 0 jar from `e9c34e7`.

On 3.5 (`rehearsal.py write`): admin form login -> `SESSION` in Redis; admin-issued magic link for `snapuser`
(not redeemed); box + item + PNG upload; `snapuser` password login, MCP client provisioned, PKCE
authorize/consent/token -> access + refresh token. (A control check on 3.5 itself passed every point below.)
Stop 3.5, start 4.1 on the same Postgres and Redis (`Schema "public" is up to date`), `rehearsal.py check`:

| Check | Result on 4.1 |
|---|---|
| 3.5 session cookie, `GET /api/v1/me` | 200, `authenticated:true`, admin - still logged in |
| 3.5 session cookie, `GET /api/v1/boxes` | 200 |
| magic link issued by 3.5 | `POST /api/v1/auth/magic-login` 200 `{"success":true}`, `/me` = snapuser |
| MCP access token issued by 3.5 | `tools/call search_boxes` 200 with results |
| MCP refresh token issued by 3.5 | 200, new tokens; call with the new access token 200 |
| data | boxes 1, 2 (dump) + boxes written by 3.5 with their items; description equal; users snapuser, admin |
| images | dump image and 3.5-uploaded images 200 `image/jpeg` |
| 4.1 log | 0 ERROR; WARN only the Spring AI / BeanPostProcessorChecker startup lines |

So the expected forced re-login does not happen: 4.1 reads 3.5 Redis sessions (as `RedisSessionCompatibilityTest`
predicts) and the 3.5 MCP authorization keeps working.

## Step 5: rollback rehearsal 4.1.2 -> 3.5.16

On 4.1 (`rehearsal.py write`, same databases, continuing from step 4): admin login (new Redis session), new magic
link, box `Room-41` with a 16 800-character unicode description (`Größe äöü ß € 日本語 🚚📦 ...`, stored as
large object oid 16678, 12 LOs in total), item + image upload, `snapuser` MCP authorization (row written by
Spring Security 7 / Jackson 3). Stop 4.1, start 3.5 on the same Postgres and Redis (`Schema "public" is up to date`):

| Check | Result on 3.5 |
|---|---|
| `Box.description` (LO written by Hibernate 7) | read back exactly equal (16 800 chars) |
| items, users | all present (Item-41, Item-35, Item-35b, Candle, Reading lamp; snapuser, admin) |
| images (incl. uploaded by 4.1) | 200 `image/jpeg` |
| magic link issued by 4.1 | 200 `{"success":true}`, `/me` = snapuser |
| MCP access token issued by 4.1 (row in Jackson 3 JSON) | `tools/call` 200 with results |
| MCP refresh token issued by 4.1 | 200, new tokens; call with them 200 |
| **4.1 session cookie** | **`GET /api/v1/me` 500, `GET /api/v1/boxes` 500** (Tomcat HTML error page) |

Cause in the 3.5 log, for every request carrying the cookie:

```
org.springframework.data.redis.serializer.SerializationException: Cannot deserialize
Caused by: java.lang.ClassNotFoundException: org.springframework.security.core.authority.FactorGrantedAuthority
```

Spring Security 7 adds a `FactorGrantedAuthority` (authentication factor) to every login; the JDK-serialized
security context in Redis cannot be loaded by Security 6. The session filter fails before the security chain can
treat the request as anonymous, so the user gets 500 on every request until the cookie is dropped or the session
expires (`spring.session.timeout` 122400 s = 34 h). Requests without the cookie and fresh logins on 3.5 work.

Mitigation, verified: delete the session keys right after rolling back, e.g.
`redis-cli --scan --pattern 'spring:session:*' | xargs redis-cli del`. Afterwards the same 4.1 cookie gives
`/api/v1/me` 200 anonymous and `/api/v1/boxes` 401 - users simply log in again. The rollback procedure
(Task 12 upgrade notes: "rollback = redeploy previous image") must include this step.

Nothing else breaks on rollback: no schema change (still V14), LO descriptions, MCP authorizations and magic
links written by 4.1 are read by 3.5.

## Step 6: CVE rescan

Runtime classpath: `mvn dependency:list -DincludeScope=runtime` (165 artifacts, e.g. spring-core/webmvc 7.0.10,
tomcat-embed-core 11.0.27, jackson-databind 3.1.7 (`tools.jackson`), MCP SDK `mcp-core` 2.1.0, Spring AI 2.0.2,
netty 4.2.19.Final, log4j-api 2.25.5, logback 1.5.38), queried as `pkg:maven/<g>/<a>@<v>` purls in one
`POST https://api.osv.dev/v1/querybatch`.

- 4.1 set: **0 advisories**.
- Control (same script on the 3.5.16 set, 141 artifacts): `spring-webmvc 6.2.19` GHSA-j9f9-w8pj-32f8
  (CVE-2026-47890), GHSA-pc63-qcmh-9cmg (CVE-2026-47884); `mcp-core 0.18.3` GHSA-8jxr-pr72-r468 (CVE-2026-35568).
  All three are gone in 4.1; nothing remains for Spring Framework, MCP SDK, Jackson, Tomcat, Netty or log4j.
- Frontend `npm audit`: 0 vulnerabilities.

Docker images (`docker scout cves`, Docker Scout 1.27.0):

| Image | Result |
|---|---|
| backend (`eclipse-temurin:21.0.13_9-jre` + app) | 396 packages, no vulnerable package |
| frontend (`nginx:1.30-alpine`) | curl 8.22.0-r0 7C/17H, brotli 1.2.0-r1 1C, libxml2 2.13.9-r2 1H/5M/5L, musl 1 unknown - all base-image OS packages; Scout recommends `nginx:1.30-alpine-slim` (0 known) |
| `postgres:15.19` (upstream) | golang stdlib 1.24.6 in gosu 4C/30H, sqlite3 4H, libxml2 2H, glibc 1H, zlib 1H, rest low |
| `redis:7.4.11-alpine` (upstream) | busybox 1M, util-linux 1M |

None of these come from the Boot migration; the frontend base image is the actionable one.

## Defects found

1. **Fixed (`a41fd9c`)**: MCP SDK 2.1 validates `tools/call` arguments against the input schema before the tool
   runs. Calls with missing/invalid arguments bypassed `BoxPilotMcpTools.execute`: no audit line, no metric, and
   the client got the validator's text in the JVM default locale. Fix: `McpStatelessSyncServerCustomizer` with
   `validateToolInputs(false)` in `McpConfiguration`; new test
   `McpAuditLogIntegrationTest.aCallWithInvalidArgumentsIsAuditedAndAnsweredWithTheGenericMessage` (failed before
   the fix, passes after).
2. **Not fixable on this branch, needs a runbook step**: rollback to 3.5 with Redis sessions written by 4.1 gives
   500 on every request of those users until their sessions are deleted (see step 5).

## Reproduction

Scripts (outside the repo, throwaway): `snapshot.py` (unchanged from baseline-3.5), `diffsnap.py` (JSON compare),
`rehearsal.py write|check <state.json> <label>` (imports `snapshot.py`'s client, PKCE flow and PNG), `osv_scan.py`
(parses `dependency:list` output, posts the querybatch, fetches aliases per hit). Environment for step 4/5 as in
`baseline-3.5/README.md` step 6 plus `APP_MCP_ENABLED=true SPRING_AI_MCP_SERVER_ENABLED=true
APP_MCP_BACKEND_URL=https://localhost:8080 APP_MCP_REDIRECT_URIS_0=http://localhost:41000/callback`.

# Task 11b: browser end-to-end and MCP end-to-end (steps 3 and 4)

Branch `feature/spring-boot-4` at `14f0fff`; no source change was needed (no defect of the migration found).
Everything ran on Windows 11 / Docker Desktop with throwaway secrets and a throwaway compose project (`bp-sb4-e2e`,
own network, own volume, all removed afterwards). Screenshots, traces and scripts stayed outside the repository.

## Setup

- Stack: `docker compose` from the worktree (images built from it: backend 4.1.2, frontend with MUI 9 / React
  Router 7 / Vite 8 behind `nginx:1.30-alpine`), `postgres:15.19`, `redis:7.4.11-alpine`, profile `prod`
  (Redis sessions, Flyway), temporary `.env`, an override that renames the containers, puts Postgres on a project
  volume, logs/images on scratch directories, drops the external `traefik-proxy` network and publishes
  frontend `127.0.0.1:13000` and backend `127.0.0.1:18080`. `VITE_API_BASE_URL=http://localhost:18080` (the SPA
  calls the backend cross-origin; cookies `SameSite=Lax`, not `Secure`, because of plain http).
- Mock Nextcloud: a small Node OIDC provider on the host (discovery, authorize with a login form, code
  exchange, RS256 id token with a stable JWKS key, userinfo, logout page), issuer `http://host.docker.internal:19999`.
  BoxPilot's own `/oauth2`, `/login` and `/api` endpoints are the real ones.
- Browser: Playwright (`playwright-core`) driving Microsoft Edge 156 (Chromium). The Playwright MCP plugin could
  not start (it looks for Chrome under `AppData\Local\Google`, which is not installed).
- Baseline for oddities: a 3.5.16 backend/frontend image built from `e9c34e7`, swapped into the same compose
  project and data (`up -d --no-build` with an image override).
- Pitfall for anyone repeating this: `docker compose` reads `CLIENT_ID`, `CLIENT_SECRET` and similar names from the
  process environment before `.env`; unset them or the stack silently uses the host's values.

## Step 3: functional matrix in the browser (4.1.2)

| # | Check | Result | Evidence |
|---|---|---|---|
| 1 | Username/password login (wrong password, then right one) and logout | pass | wrong password: `401 {"error":"Invalid credentials"}` and the message in the form; login lands on `/app/boxes`, `/me` = admin; after logout `/app/boxes` shows the login form and `GET /api/v1/boxes` is `401`. Logout answers `302` to `NEXTCLOUD_LOGOUT_URL?redirect_uri=<frontend>` as in the baseline (`95-logout`) |
| 2 | OIDC login through mock Nextcloud | pass | SPA button -> real `/oauth2/authorization/nextcloud` (PKCE `S256`, state, nonce) -> mock login form -> real callback `/login/oauth2/code/nextcloud` -> `/app/boxes`; `/me`: `authProvider NEXTCLOUD`, `username mockuser`, name from the `name` claim, role USER |
| 3 | OIDC rejection (user not allowed) | pass | admin disabled the OIDC user in the admin panel; the next OIDC login ends on the login form, `/me` anonymous, log `OIDC login rejected for disabled user`. The form shows no explanation (the failure URL `?error=login_failed` is not rendered) |
| 4 | Magic link create (admin) | pass | user row -> "Magic-Link generieren" -> link `<frontend>/magic#t=<uuid>`, valid for 24 h |
| 5 | Magic link redeem | pass | fresh browser context: lands on `/app/boxes`, `/me` = alice / USER |
| 6 | Magic link reuse rejected | pass | page "Dieser Login-Link ist ungültig oder abgelaufen"; log `token of user 'alice' is used or expired`. Expiry by clock was not run on its own; the service handles used and expired in one path with one generic 401 |
| 7 | CSRF-protected writes from the SPA | pass | every create/update/delete/upload of the run worked; without a token `POST /api/v1/boxes` answers `403` (as baseline `37-err-csrf-missing-as-user`) |
| 8 | Admin panel: user list, create/edit user, MCP switch (MUI 9 `Switch`, `role=switch`), magic links | pass | list shows admin/alice/OIDC user with role, status, auth type; the "Aktiviert" and "MCP" switches toggle and persist (re-opened dialog shows the value); the "Einstellungen" tab is the existing "Coming soon" placeholder |
| 9 | Boxes create / list / bulk flags / delete, box number pool | pass | boxes 1 and 2 (unicode description intact), bulk "Umgezogen" and "Label gedruckt", delete; pool row 3 goes `is_available false -> true` on delete and the next box reuses number 3 |
| 10 | Items create / edit / delete | pass | `Stehlampe`, `Kerze` renamed `Teelicht`, `Wollmütze ü`, delete with confirmation |
| 11 | Item image upload, thumbnail, public token URL, replace, delete | pass | PNG upload `POST /items/{id}/image` 200; shown as thumbnail `image/jpeg` 200x150 from `/api/v1/public/items/{token}/image`; `/image/large` 200; bogus token 404 JSON; replace works; delete removes the image |
| 12 | Move item between boxes | pass | "Verschieben" -> target box -> confirm; item gone from box 2, present in box 1 |
| 13 | Label PDF | pass | react-pdf builds the PDF in the browser and opens a blob tab: valid `%PDF-1.3`, 1 page, 5 kB, 2 images (QR) and 2 fonts; box marked "Label gedruckt" |
| 14 | Public share / preview (valid, invalid, revoked) | pass | valid UUID: box and items without login; unknown UUID and `not-a-uuid`: "Box konnte nicht geladen werden" (404); deleted box: same; bogus image token 404; image of a deleted image: see finding B |
| 15 | Language switch | pass | DE <-> EN via the flag menu, all labels change, survives reload (cookie) |
| 16 | Error handling (401/403/404) | pass | user opening `/app/admin` is redirected to `/app/boxes`; `GET /api/v1/admin/users` as user `403`; unknown SPA routes redirect to `/app/boxes`; anonymous protected page shows the login form, API `401` |
| 17 | Responsive (390x844, box list and admin) | pass | no horizontal scroll, burger menu in the app bar, cards stack; two buttons under 36 px height (filter reset, "Erweiterte Filter") |

17 of 17 pass (magic-link expiry by clock not exercised on its own, see 6).

Console / network noise (everything not listed was empty):

| Where | Message | Expected? |
|---|---|---|
| login | `401 POST /api/auth/login` | yes, provoked wrong password |
| `/magic#t=...` with a used or bogus token | `401` or `403` on `POST /api/v1/auth/magic-login` | yes, see finding A |
| public page, unknown box | `404 GET /api/v1/public/<id>` | yes |
| logout | `Logout failed: AxiosError: Network Error` (the XHR follows the `302` to the Nextcloud logout URL cross-origin: CORS) | existed before the upgrade; the session is still ended and the user lands on the login form |
| deleted item image | `500 GET /api/v1/public/items/<token>/image` | finding B, same on 3.5.16 |
| mock provider | `404` for the mock's favicon | test artefact |

No MUI/React warnings, no failed asset requests, no cookie or `SameSite` warnings, no deprecation messages.

Findings that are not Spring Boot regressions (reproduced on the 3.5.16 image on the same data):

- **A (low-medium, pre-existing): magic-link redeem races the app's own `/me` call.** `redeemMagicLink` fetches
  `/api/v1/csrf` and sends that token, but the SPA's `GET /api/v1/me` runs in parallel and also sets an
  `XSRF-TOKEN` cookie; when it answers last it replaces the cookie, the sent token no longer matches and the redeem
  `POST` is answered `403` before the token is looked at. The user sees "link invalid" for a valid link (the token is
  not consumed; loading the link again usually works). Eight cold loads of `/magic#t=<bogus>`: 4.1.2 gave 401 (race
  won) 1x and 403 7x, 3.5.16 gave 401 4x and 403 4x. Idea for a later task: request the CSRF token after `/me`
  settled, or retry once on 403.
- **B (low, pre-existing): the public image URL of an item whose image was deleted answers `500 Image not found`**
  (ERROR log line) instead of 404; identical on 3.5.16 (the baseline README already notes this).
- **C (cosmetic, pre-existing): the three-dot button on a box card shows the raw i18n key `boxes.actions`** as its
  tooltip and accessible name (`BoxList.tsx` uses `t('boxes.actions')`; the key exists in another locale section).

## Step 4: MCP end to end (4.1.2, `APP_MCP_ENABLED=true`)

Same stack with `BACKEND_URL=https://localhost:18080` (the `prod` start guard demands https; the traffic itself was
plain http on `localhost:18080`, so absolute URLs in the metadata and the consent redirect say `https` and were
rewritten by the test client), redirect URIs through `APP_MCP_REDIRECT_URIS_n` variables,
`APP_MCP_IMAGE_CALLS_PER_DAY=5`. Driven with the real SPA for provisioning/login/consent and a script for the OAuth
and JSON-RPC legs.

| Check | Result | Evidence |
|---|---|---|
| Discovery documents | pass | `/.well-known/oauth-authorization-server` (issuer, authorize/token/revoke endpoints, `code_challenge_methods_supported:["S256"]`, `token_endpoint_auth_methods_supported:["none"]`, scope `mcp:read`) and `/.well-known/oauth-protected-resource/mcp` (resource `.../mcp`, bearer header) both 200 |
| Client provisioning from the SPA ("Claude-Verbindung" dialog) | pass | user with MCP allowed sees the button; the dialog shows client id and connector URL (`<VITE_API_BASE_URL>/mcp`), "Neu erzeugen" and "Alle Verbindungen widerrufen" |
| Authorize + consent + token with PKCE | pass | `/oauth2/authorize` -> consent page ("Angemeldet als: alice", "Weiterleitung an: 127.0.0.1", read-only text) -> approve -> `302` to the loopback redirect with `code` and the original `state` -> `/oauth2/token` with `code_verifier`: 200 access + refresh token, `expires_in` 1799, scope `mcp:read`. Replaying the code: `400 invalid_grant` and the tokens issued from it are invalidated (log `Invalidated authorization token(s)`) |
| Refresh | pass | new pair issued; the previous access token answers 401 afterwards |
| `initialize`, `tools/list` | pass | protocol `2025-06-18`; exactly `get_box_contents`, `get_item_image`, `search_boxes`, `search_items`, each `readOnlyHint:true` |
| `search_items`, `search_boxes`, `get_box_contents` | pass | correct data (item id, box number, rooms, flags, item counts); no uuid, token or url in the payloads |
| `get_item_image` | pass | text part `{"boxNumber":1,"name":"Teelicht"}` plus an image part `image/jpeg` (valid JPEG, 1150 bytes); item without image or unknown id: "This item has no image." (no enumeration) |
| Bad arguments: missing `query`, 5000-character `query` | pass | HTTP 200, `isError:true`, `Invalid input. Check the parameters and their allowed ranges.`, audit line `result=error` (the case fixed in `a41fd9c`) |
| Bad arguments: wrong **type** (`{"boxNumber":"abc"}`, `{"itemId":"zzz"}`) | **finding D** | `isError:true` with the raw library message (4.1: `Conversion from JSON to java.lang.Integer failed ... Cannot deserialize value of type java.lang.Integer ...`; 3.5.16: `Character a is neither a decimal digit number ...`) and **no audit line**. The same class of leak exists on 3.5.16, so it is pre-existing: the documented "generic message + audit line" holds for validation errors, not for argument type-conversion errors. Unknown tool: JSON-RPC `-32602`, identical to baseline `90` |
| MCP permission switched off in the admin panel | pass | next call `401`, log `MCP access revoked: userId=2`; switching it back on does not revive the old token (client and authorizations were deleted); a new client + consent works again |
| User disabled | pass | next call `401`, connections revoked; re-enabling does not revive them |
| Rate limits | pass | image tool: calls 6 and 7 of a 5/day limit answer HTTP 200 `isError:true` "Too many requests. Try again later." with audit `result=rate_limited`; per-subject 60/min: `429 {"error":"too_many_requests"}` with `Retry-After: 60` (calls of the earlier tests in the same minute count); failed bearer lookups: 429 after 30/min (log `MCP rate limit reached limit=failed_token_lookups`) |
| Audit log | pass | one `McpAuditLogger` line per call: `MCP tool call sub=<id> tool=<name> result=ok\|error\|rate_limited hits=<n> ip=<addr>` (`ip` is the docker gateway: no proxy in front); no token, uuid or argument values in any line |
| Host / Origin check on `/mcp` | pass | Host equal to the host of `BACKEND_URL`, no `Origin`: 200. Foreign Host (`evil.example`; also bare `localhost` and `127.0.0.1:18080`, because the port is part of the match): `421 Invalid Host header`. Foreign `Origin` (`https://evil.example`, `https://claude.ai`, `null`, the Inspector UI's `http://localhost:6274`, and the right host with another scheme): `403 Invalid Origin header`. `Origin` equal to the origin of `BACKEND_URL`: 200. No token: 401 with `WWW-Authenticate: Bearer resource_metadata=...`; bad token 401; `GET /mcp` 405 |
| MCP Inspector (`npx @modelcontextprotocol/inspector --cli ... --transport http`) | pass | `tools/list` and `tools/call search_boxes` with the bearer token work. Captured against a stub server: the CLI sends `Host: <target host:port>`, `User-Agent: node` and no `Origin`, so it passes the check. (The Inspector's browser UI talks to its own local proxy; the browser's `Origin` does not reach `/mcp`.) Its interactive OAuth click-through was not driven (headless run) |
| `APP_MCP_ENABLED=false` | pass | `/mcp` anonymous `403` (POST without CSRF); `GET /oauth2/authorize`, `/oauth2/jwks`, `/oauth2/consent` and both `/.well-known/...` documents `302` to the frontend (the ordinary login redirect); `POST /oauth2/token` and `POST /api/v1/me/mcp-client` `403`; `GET /api/v1/me/mcp-client` `401`; no `Registered tools` / start-guard log line. Status codes identical to 3.5.16 with the flag off |
| `APP_MCP_ENABLED=false` plus `SPRING_AI_MCP_SERVER_ENABLED=true` | pass | variable present in the container, no tool registration, same statuses as above |

Log scan of the run (`logs/boxcopilot.log`, 3.5 and 4.1 starts mixed): 0 lines matching
`jackson 2|com.fasterxml|deprecat`; ERROR lines only the two `File storage error: Image not found` (finding B); WARN
lines are the provoked cases plus the known Spring AI `No resource/prompt/complete methods found` and
`BeanPostProcessorChecker` lines.

Matrix totals: browser 17 pass / 0 fail; MCP 15 pass / 0 fail with one pre-existing finding (D) beside the pass.

### What could not be tested here

- **The real Claude.ai connector (S12 manual check stays open).** To be confirmed once against a deployment on public
  https: which `Origin` (if any) and `Host` claude.ai sends to `/mcp`. The new check accepts a request without
  `Origin` and one whose `Origin` equals the origin of `BACKEND_URL`, and refuses everything else (an `Origin` of
  `https://claude.ai` is refused with 403); if Claude.ai sent one, the connector would break. Steps (from
  `docs/ops/mcp-server.md`, "Manual MCP Inspector / Claude Free verification"): backend on public https with
  `BACKEND_URL` equal to that origin, Traefik passing `Host` through and forwarding `X-Forwarded-*`;
  `app.mcp.redirect-uris` containing `https://claude.ai/api/mcp/auth_callback` (and the loopback callback for the
  Inspector, removed again afterwards); a test user with MCP allowed, a box with an item with a photo; then
  claude.ai -> Settings -> Connectors -> "Add custom connector", name `BoxPilot`, URL `https://<backend>/mcp`, client
  id from the "Claude-Verbindung" dialog in the advanced settings, no secret; connect (login, consent page), ask for
  inventory by room and for a photo, repeat in the mobile app, idle past the 30-minute token life and use again
  (refresh, watch for an unexpected consent prompt), finally "Alle Verbindungen widerrufen". During this, watch the
  backend log and the proxy access log for a `403` or `421` on `/mcp` and note the request's `Host` and `Origin`.
- The Inspector's interactive OAuth/browser mode, a real Nextcloud tenant, expiry of an MCP authorization by the
  `max-connection-age` clock, a magic link expiring by the clock, and https end to end (the metadata URLs are https
  only because the `prod` guard demands it).
