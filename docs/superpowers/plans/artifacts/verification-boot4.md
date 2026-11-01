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
