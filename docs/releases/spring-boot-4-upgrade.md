# Upgrading from the Spring Boot 3.5 build


The backend now runs on Spring Boot 4.1 (Spring Framework 7, Spring Security 7, Hibernate 7, Jackson 3, Spring AI 2).
Spring Boot 3.5 open-source support ended on 2026-06-30; 4.1.x is supported until 2027-07-31 (endoflife.date). The
upgrade was rehearsed against a dump of a 3.5 installation.

**Before you upgrade**
- Take a database dump, e.g. `docker exec boxcopilot-postgres pg_dump -U <POSTGRES_USER> <POSTGRES_DB> > boxpilot-3.5.sql`.
  The upgrade adds no Flyway migration (the schema stays at V14) and Flyway reports "up to date", so the dump is only
  needed if you want to discard data written by 4.1 later.
- The backend has a healthcheck on `GET /actuator/health` (`start_period` 120s) and the frontend waits for it to be healthy
  (`depends_on: condition: service_healthy`). If the backend never becomes healthy, `docker compose up -d` reports the
  failed dependency and the frontend stays down.
- The backend image sets `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75`, which raises the heap ceiling from the JVM
  default (25%) to 75% of the memory the container sees. The compose file sets `mem_limit: 1g` for the backend; if you
  raise or remove it, adjust the percentage.
- Postgres is now pinned to `postgres:15.19` (Debian 13 / trixie); the previous `postgres:15` was already a moving
  tag. Before upgrading run `docker exec boxcopilot-postgres cat /etc/os-release`. If the running image is Debian 12
  (bookworm), either pin `postgres:15.19-bookworm` or `REINDEX` your text indexes after the swap (glibc collation
  changes).
- *Non-root backend:* since 0.2.0 the backend runs as uid/gid 10001 with a read-only root filesystem. See the
  [0.2.0 release notes](0.2.0.md) for the one-time `chown` of `logs` and `images` and how to go back to root.

**What carries over unchanged**
- Users usually stay logged in: Redis sessions written by 3.5 are normally still read by 4.1. A stored session value
  that 4.1 cannot deserialize (Spring Security 6 vs 7 class differences) is treated as no session: the request is
  anonymous (401 / login page), the user signs in again, and one WARN is logged per backend start. It is never a 500.
- MCP access and refresh tokens issued by 3.5 stay valid, and so do unredeemed magic links.
- `Box.description` is still stored as a PostgreSQL large object (the column holds the OID as text), exactly as before.

**What changes**
- JSON object keys in REST and MCP response bodies are now in alphabetical order (Jackson 3 default). The order has no
  meaning and the frontend reads by key, but anything that compares response text byte for byte will see a difference.
  The MCP `tools/list` schemas also gain a `$schema` entry; tool names are unchanged.
- Cookies that the server clears (`XSRF-TOKEN`, `JSESSIONID`) carry `Expires=Thu, 01 Jan 1970 ...` but no longer
  `Max-Age=0` (Tomcat 11). Browsers still delete them.
- The `/mcp` Host/Origin check is derived from `BACKEND_URL`. The `Host` header must equal the host (and port, if
  `BACKEND_URL` names one) of `BACKEND_URL`, otherwise the answer is 421. An `Origin` header, if present, must equal
  the origin of `BACKEND_URL`, otherwise 403; clients that send no `Origin` pass. A default port in
  `BACKEND_URL` (`:443` for https, `:80` for http) is ignored for this check, since clients send `Host` without
  it; a non-default port must appear in `Host`. Traefik must pass the original `Host` through.
- `APP_MCP_ENABLED` is the only MCP master switch. `SPRING_AI_MCP_SERVER_ENABLED=true` on its own no longer
  enables anything while `APP_MCP_ENABLED` is not `true`.
- Sessions expire 30 minutes after last use (1800 s, set by `@EnableRedisHttpSession` in `RedisConfig`). The dead
  `spring.session.timeout: 122400s` key was removed from `application-prod.yml`; the effective lifetime is unchanged.

## Rolling back to the 3.5 build

1. Stop the 4.1 backend (`docker compose stop backend`), so it cannot write new sessions.
2. Delete the Redis session keys. Spring Security 7 stores a `FactorGrantedAuthority` in every session; Boot 3.5
   cannot deserialize it and answers **500 on every request that carries a session cookie written by 4.1**
   (`ClassNotFoundException ... FactorGrantedAuthority`) until that session expires (at most 30 minutes after its
   last use) or is removed. With the compose
   file's Redis (it sets `REDISCLI_AUTH` from `REDIS_PASSWORD`, so `redis-cli` inside the container authenticates):
   ```bash
   docker exec boxcopilot-redis sh -c "redis-cli --scan --pattern 'spring:session:*' | xargs -r redis-cli del"
   ```
   Afterwards the stale cookies are treated as anonymous and users log in again.
3. Start the previous (3.5) backend and frontend images.
4. Data written by 4.1 (descriptions, items, images, MCP authorizations, magic links) is readable by 3.5, so the
   dump does not need to be restored. Restore it only if you want to discard everything written since the upgrade.

## Known issues (not caused by the migration)

Found during the Boot 4 verification; each also occurs on the 3.5 build, except where noted. Rejected uploads no longer remove the stored image, and over-size uploads and wrong content types get a 4xx since 0.2.0.
- (Fixed: now 400.) A missing request parameter or a missing multipart part was answered with 500 by the catch-all handler instead of a 4xx.
- (Fixed in the frontend: `redeemMagicLink` now sends a per-request CSRF header and retries once on 403.) Redeeming a magic link can race the SPA's own `/api/v1/me` call for the `XSRF-TOKEN` cookie, so a valid link can
  show "invalid" without being consumed; loading the link again normally works. In testing this happened more often
  on 4.1 (7 of 8 cold loads) than on 3.5 (4 of 8).
- (Fixed: now 404 with a JSON body.) The public image URL of an item whose image was deleted answered 500 instead of 404, because an `Accept: image/*` request could not be given the JSON error body.
- (Fixed: fixed error text, one audit line, result `error`; the user gate and image quota are not consulted for such calls.) MCP tool calls with arguments of the wrong type (e.g. a string for a number) returned the library's raw message and
  wrote no audit line. Missing or out-of-range arguments are handled and audited.
- The frontend base image `nginx:1.30-alpine` showed OS-package CVEs in `docker scout` at test time (8 critical,
  18 high: curl, brotli, libxml2). The frontend image now builds on `nginx:1.30-alpine3.24` (1.30.7, the newest
  stable line) and runs `apk upgrade --no-cache`; rescan with `docker scout` after rebuilding. The backend image
  showed none.

