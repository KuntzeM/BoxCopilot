# Baseline on Spring Boot 3.5.16 (Task 0)

Reference captures from the last 3.5.16 build (commit `e9c34e7` plus the migration plan, no source changes). Later
tasks (3, 11) re-run the same steps against the 4.1 build and diff against these files.

Nothing here is a live credential: every password, token and key was created for the capture in throwaway
containers. Files `NN-*.json` are scrubbed anyway (tokens, codes, cookies, UUIDs, timestamps, base64 payloads,
`?t=` cache-busters are replaced by placeholders; numeric ids are kept because the database starts empty).

## Contents

| File | What |
|---|---|
| `NN-*.json` (96 files) | request + response (status, selected headers, Set-Cookie attribute names, body) for every endpoint in `controller/*` (incl. admin, magic link, public), `/api/v1/me`, `/api/v1/csrf`, the MCP endpoints/authorization server, and error cases (`err-*`: 400 invalid body / malformed JSON / wrong type, 401, 403 incl. missing CSRF, 404, upload of non-image, 16 MB upload, missing parameter) |
| `mcp-tool-names.txt` | the four MCP tool names; the full `tools/list` response is `85-mcp-tools-list.json` |
| `legacy-authorization-row.sql` | the `oauth2_registered_client` row and the `oauth2_authorization` row produced by the real authorization-code + refresh flow (taken before the revoke step), as `INSERT` statements, for the Spring Authorization Server migration test |
| `redis-session-fixture.txt` | one `spring:session:sessions:*` hash from a Redis 7.4.11 after a form login (JDK-serialized fields; escaped, base64 and `DUMP` forms) |
| `snapshot.py`, `redis_session_dump.py` | the scripts that produced the above |

Outside the repo (git-ignored, never committed), `C:\Users\mathi\Coding\BoxPilot-baseline\`:
`boxpilot-3.5.dump` (`pg_dump -Fc`), `boxpilot-3.5.sql` (plain, `--no-owner`), `pgdata/` (copy of the stopped
Postgres 15 data directory, taken with `docker cp`; restore with `chown -R 999:999`), `images/` (image store of
the snapshot run). The dump holds 2 users, 2 boxes, 2 items, 1 live `oauth2_authorization`, 13 Flyway history rows
(V1, V3-V14) and the Spring Session JDBC tables.

## How it was produced

Everything ran on the host, outside the repo tree. The Nextcloud OIDC provider is unavailable, so a static OIDC
discovery document is served on `localhost:9999` (the backend only needs discovery at startup, there is no OIDC
login in these captures); login is by username/password and magic link.

1. Build (copy of `backend/` without `target/`, so the Java language server cannot interfere):
   `mvn -B clean verify` -> `Tests run: 645, Failures: 0, Errors: 0, Skipped: 0`, BUILD SUCCESS, Docker running
   (Testcontainers Postgres tests included).
2. Containers: `postgres:15` (db `boxpilot`, user `bp`, port 55432) and `redis:7.4.11-alpine`
   (`--requirepass redis-throwaway`, port 56379).
3. Mock OIDC: a Python server answering `GET /.well-known/openid-configuration` with
   `{"issuer":"http://localhost:9999","authorization_endpoint":...,"token_endpoint":...,"jwks_uri":...,
   "userinfo_endpoint":...,"response_types_supported":["code"],"subject_types_supported":["public"],
   "id_token_signing_alg_values_supported":["RS256"]}` and `Content-Type: application/json`.
4. API + MCP snapshot (backend: `java -jar backend-0.1.0.jar`, profile `dev` on real Postgres, Flyway migrations,
   MCP on):
   ```
   SPRING_PROFILES_ACTIVE=dev
   SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/boxpilot  (user bp / password bp-throwaway)
   SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver
   SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.PostgreSQLDialect  SPRING_JPA_HIBERNATE_DDL_AUTO=none
   SPRING_FLYWAY_ENABLED=true SPRING_FLYWAY_BASELINE_ON_MIGRATE=true SPRING_FLYWAY_VALIDATE_ON_MIGRATE=false
   ADMIN_USERNAME=admin ADMIN_PASSWORD=<throwaway>  APP_IMAGE_STORAGE_PATH=<empty dir>
   APP_MCP_ENABLED=true SPRING_AI_MCP_SERVER_ENABLED=true APP_MCP_BACKEND_URL=http://localhost:8080
   APP_MCP_REDIRECT_URIS_0=http://localhost:41000/callback
   NEXTCLOUD_URL=http://localhost:9999 NEXTCLOUD_LOGOUT_URL=http://localhost:9999/logout
   FRONTEND_URL=http://localhost:3000 CLIENT_ID=mock CLIENT_SECRET=mock
   ```
   (`dev` normally runs on H2 and sessions in JDBC; the datasource override puts it on Postgres, sessions stay
   in the Postgres `spring_session*` tables. The committed `application-dev.yml` does not enable MCP, hence the
   `APP_MCP_*` / `SPRING_AI_MCP_SERVER_ENABLED` variables.) Start from an empty schema
   (`drop schema public cascade; create schema public;`) and an empty image directory, then
   `ADMIN_PASSWORD=<same> PG_CONTAINER=<postgres container> PG_USER=bp PG_DB=boxpilot python snapshot.py`.
5. Seed data (created by `snapshot.py`): admin logs in by form, creates users `snapuser` (password set, MCP
   allowed) and `scratchuser` (deleted again); `snapuser` logs in via an admin-issued magic link; creates boxes
   `Cellar` (id 1, fragile, label printed, no-stack), `Attic` (id 2), `Garage` (id 3, deleted), items
   `Reading lamp` (with a PNG image), `Wool blanket`, `Candle`; then provisions an MCP client, walks the PKCE
   authorization-code flow (authorize, consent page, approve, token), calls `initialize`, `tools/list` and all four
   tools, refreshes, dumps the authorization row, regenerates the client, revokes all connections, and finally
   leaves one fresh authorization behind before `pg_dump`.
6. Redis session: backend with profile `prod` (Postgres db `boxpilot_redis`, Redis sessions, Flyway), the same
   mock OIDC, `REDIS_HOST=localhost REDIS_PORT=56379 REDIS_PASSWORD=redis-throwaway`,
   `SERVER_SERVLET_SESSION_COOKIE_SECURE=false SERVER_SERVLET_SESSION_COOKIE_SAME_SITE=lax` (plain-http host
   run), form login as admin, then `REDIS_PORT=56379 REDIS_PASSWORD=redis-throwaway python redis_session_dump.py`.
   Only one key exists (`spring:session:sessions:<id>`, no `expires:`/`expirations:` index keys).
7. Dump: `pg_dump -Fc` and plain `pg_dump --no-owner` from the snapshot database, then `docker stop` +
   `docker cp <container>:/var/lib/postgresql/data pgdata`.

The CSRF header must carry the value returned by `GET /api/v1/csrf` (the token is masked per request, the cookie
value is not accepted), and must be re-fetched after login.

## Findings worth knowing before comparing

- `mvn verify` passed with `Skipped: 0` only after discarding the *uncommitted* `application-dev.yml` change
  (it turns the MCP starter on in the `dev` profile, which makes `McpFlagOffRealChainsTest.neitherMcpNor...` fail
  with 1 failure of 645). The clean `HEAD` tree is green.
- Several error cases answer **500** on 3.5 (all with `{"message":"An unexpected error occurred","status":500,...}`):
  missing `q` on `GET /items/search`, upload of a non-image, upload above 15 MB (`Maximum upload size exceeded`),
  upload without `file`. These are baseline behavior, not expectations; a changed status on 4.1 is a diff to
  review, not automatically a regression.
- A rejected image upload for an item that already has an image left that item without stored image files
  (the public image answered 500 `Image not found` afterwards); the scripts therefore fetch the public image
  before the failing uploads.
- Responses of the `mcp-*` calls to `/mcp` come as JSON (stateless transport); error cases for tools answer HTTP 200
  with a JSON-RPC body.
