# Security audit – main @ 4813985 (Spring Boot 4.1.2) – 2026-11-19

Scope: read-only source review by 4 parallel agents (auth/session, access control/sharing, MCP + RCE-class sinks, frontend/deploy) plus dependency checks. No finding was dynamically reproduced; Docker runtime test not yet done (daemon would not start).

## Baseline check
- Worktree fast-forwarded to `origin/main` = merge of PR #36 (Boot 4 migration). Parent `spring-boot-starter-parent 4.1.2`, Spring AI 2.0.2, Java 21, Hibernate 7.4.13, Tomcat 11.0.27, Jackson 3, Flyway 12.4.0.
- Migration is present on main (migration/refactor/docker/docs commits). Not yet verified by a full `mvn verify` / Docker run.

## Dependencies
- `npm audit` (frontend, 332 pkgs): **0 vulnerabilities**.
- Java: no CVE database scan done yet (maven-mcp unavailable; OWASP `dependency-check-maven 12.1.3` is configured in a profile in `backend/pom.xml` – run it, needs NVD access). Versions look current.
- **Log4Shell**: log4j-core absent; only `log4j-api`/`log4j-to-slf4j` 2.25.5 (bridge, no lookups) + Logback 1.5.38. No JNDI, no Java deserialization, no Jackson default typing, no SpEL/SnakeYAML(`new Yaml`)/XXE/SSRF/Spring4Shell binding sinks found.

## Findings (no Critical/High)
| # | Sev | Finding | Fix | Status |
|---|-----|---------|-----|--|
| 1 | Med (needs validation) | Any OIDC (Nextcloud) account is auto-provisioned as enabled USER with full read/write/delete on all data (`UserService.createOrUpdateOidcUser`) | Allow-list/group claim or provision disabled + admin approval | Done |
| 2 | Low-Med (needs validation) | Trusted-proxy regex trusts all private ranges; nginx forwards client `X-Forwarded-For` → login/MCP throttle bypass if backend reachable from non-Traefik peer | Pin `TRUSTED_PROXY_PATTERN` to Traefik; nginx `real_ip`/overwrite XFF; set XFP from incoming | Done |
| 3 | Low-Med | Public image URLs cached `public, max-age=1y, immutable`; `imageToken` not rotated on delete/re-upload/move | Rotate token on those ops; shorter/private cache | Done |
| 4 | Med (needs validation) | CSP defaults to `report` mode; `img-src` leak if backend allows absolute `imageUrl` | Default `CSP_MODE=enforce`; always prefix API base | Done |
| 5 | Low-Med | H2 runtime dep + default datasource empty-password file DB, `ddl-auto: update`; dev profile admin default `admin` if `SPRING_PROFILES_ACTIVE=dev` leaks to deploy | StartupGuard rejects H2/dev outside test; base `ddl-auto: none` | Done |
| 6 | Low | Magic-login tokens stored plaintext | Store SHA-256 | Done |
| 7 | Low | `flyway.validate-on-migrate: false` in prod | Set `true` | Done |
| 8 | Low | Last-admin can be demoted/disabled; DoS: unbounded `description`, `itemIds`, search without limit | `@Size`, cap pagination, last-admin guard | Done |
| 9 | Low | Log injection (user text in SLF4J messages) | Strip CR/LF or JSON encoder | Done (LogSanitizer at user-input log sites; other sites log ids/system values only) |
| 10 | Low | MCP: no audience/`resource` binding; all MCP users read global dataset (by design, read-only tools) | Pin resource in test | Partly: resource metadata pinned by existing test (McpResourceServerIntegrationTest); no audience validation added (by design, read-only tools) |
| 11 | Low | CI actions pinned by tag, Docker images by tag, `npm ci \|\| npm install` fallback | SHA/digest pin, drop fallback, Dependabot | Partly: npm ci fallback removed, Dependabot added; Action/Docker tag pins to SHA/digest not done (needs network lookup, Dependabot will propose) |
| 12 | Info | Public image with token but no image → 500 | 404 | Done (ImageNotFoundException -> 404) |

Verified sound: CSRF, session fixation, magic-link atomic single-use, generic login errors, BCrypt, actuator only `health`, no XSS sinks, no token in localStorage, image re-encode/path guards, JPQL bound params, non-root read-only backend container, MCP Host/Origin validation and bearer-only chain.

## Implementation plan
1. **P1** (#1, #2, #4): OIDC provisioning gate; proxy trust + nginx header handling; CSP enforce default.
2. **P2** (#3, #5, #6, #7, #8): token rotation, startup guards, hashed magic tokens, Flyway validate, size caps, last-admin guard.
3. **P3** (#9–#12): hygiene and CI/Docker pinning.
4. Run OWASP dependency-check on the Java side; bump anything flagged. Each change: nearest existing test as pattern, `mvn test`, `npm test`, `graphify update .`.
