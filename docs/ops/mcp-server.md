# MCP server — operations guide

This is the operator-facing companion to the [README MCP section](../../README.md#claude--mcp-integration) and to
the design spec [`docs/superpowers/specs/2026-09-21-mcp-server-design-v4.md`](../superpowers/specs/2026-09-21-mcp-server-design-v4.md)
(section references below point there). It covers what support staff and operators need to run the feature day
to day: client lifecycle, the kill-switch, the cleanup job, how to read the logs and metrics, and the residual
risks worth knowing about before go-live.

The feature itself: a read-only MCP server built into the Spring Boot backend, protected by a built-in OAuth 2.1
authorization server (opaque tokens, no JWT/JWKS). Package `com.boxcopilot.backend.mcp`. Disabled by default.

## Client regeneration and "revoke all connections"

Every user allowed to use MCP (`users.mcp_allowed = true`) has exactly one OAuth client, created lazily on first
use of the "Claude-Verbindung" dialog (`POST /api/v1/me/mcp-client`). The client ID is the only thing that binds
a Claude session to a BoxPilot account (spec §2.1) — there is no per-connection list to review, only "all
connections of this user".

Both actions below are self-service, from the "Claude-Verbindung" dialog reachable from the app menu. Support
does not need direct database access for the normal case:

- **Regenerate ("Neu erzeugen")** — `POST /api/v1/me/mcp-client/regenerate`. Creates a new client ID and deletes
  the old client **and every authorization issued to it**. Use this whenever a client ID may have leaked (shared
  screenshot, exposed in a shared screen, or an access-log entry as described below). The user must paste the new
  ID into Claude's custom connector again and reconnect.
- **Revoke all connections ("Alle Verbindungen widerrufen")** — `DELETE /api/v1/me/mcp-connections`. Deletes all
  stored authorizations (access/refresh tokens, codes) for the user's current client, but keeps the client ID
  itself, so Claude will simply be asked to log in and consent again on the next call; no new ID needs to be
  entered in the connector.
- Disabling a user, deleting a user, or an admin turning off `mcp_allowed` for a user has the same effect as
  regenerate: client and all authorizations for that user are deleted (`McpAccessRevokedListener`,
  `@TransactionalEventListener(phase = BEFORE_COMMIT)`, so it is atomic with the user change).
- Since 0.2.0 failed logins no longer lock accounts (they are throttled per client IP instead, see the main README),
  so a login attack cannot touch a victim's MCP connection. `McpAccessPolicy` still denies a user whose legacy
  `lockedUntil` is in the future; migration V15 cleared all such values and nothing sets them any more.
- **Setting the user's password** (Admin Panel) revokes the user's MCP access the same way as regenerate: the
  password-reset path publishes `McpAccessRevokedEvent`, and the MCP authorization chain also rejects a session older
  than the reset (`sessions_valid_after`, `McpSessionUserResolver`). The user has to enter the client ID again.

If a support engineer ever needs to act without the affected user (e.g. the user cannot access the dialog): the
same effect can be reached through the Admin Panel by toggling `mcp_allowed` off and back on for that user, which
deletes the client and forces a fresh `mcp-client` provisioning call next time the dialog is opened.

## Kill-switch

For the Spring Boot 4 upgrade and rollback notes see the main README, "Upgrading from the Spring Boot 3.5 build".

`APP_MCP_ENABLED=false` (the default) turns the entire feature off at startup: no MCP Spring beans are created,
`spring.ai.mcp.server.enabled` follows the same flag (and is forced off even when set directly, e.g. by a
profile or `SPRING_AI_MCP_SERVER_ENABLED`; see `McpStarterSwitch`). `APP_MCP_ENABLED` is the only master switch:
`SPRING_AI_MCP_SERVER_ENABLED=true` on its own enables nothing, and `/mcp`, `/oauth2/authorize`, `/oauth2/token` give no 2xx
response and no data — anonymous callers get redirected to the frontend (the existing app chain's entry point),
authenticated callers get 404 because no handler is registered for those paths. Existing rows in
`oauth2_registered_client` / `oauth2_authorization` are **not** deleted by flipping the flag; they simply become
unreachable. If a full incident response requires actually removing them, that is a manual SQL step (`DELETE FROM
oauth2_authorization; DELETE FROM oauth2_registered_client;`) run while the flag is off.

Flipping the flag requires a backend restart (it is read at startup via `@ConfigurationProperties` /
`@ConditionalOnProperty`); it is not a runtime toggle.

## Nightly cleanup job

`McpAuthorizationCleanup` runs on the cron expression `app.mcp.cleanup-cron` (env var `APP_MCP_CLEANUP_CRON`,
default `0 0 3 * * *` — 03:00 local server time daily). It deletes authorizations whose refresh token (or, if
there is none, access token) has expired, whose `boxpilot.started_at` is older than `app.mcp.max-connection-age`
(default 14 days), and expired unused authorization codes. Spring Authorization Server does not clean up after
itself, so without this job the `oauth2_authorization` table grows for as long as the feature has ever been used.

**Verifying it ran:** on a day it actually removed rows, it logs one line at INFO:

```
MCP cleanup removed {n} expired authorizations
```

It logs nothing when there was nothing to remove (silence is not failure — check `oauth2_authorization` row
counts directly if a hard confirmation is needed, e.g. `SELECT count(*) FROM oauth2_authorization;` before/after
the scheduled time). The job carries no user, client or token information in its log line by design.

## Reading the audit log and Micrometer counters

Every MCP tool call produces exactly one audit line, regardless of outcome (`McpAuditLogger`, logger
`com.boxcopilot.backend.mcp.observability.McpAuditLogger`, level INFO):

```
MCP tool call sub={userId} tool={toolName} result={ok|denied|rate_limited|error} hits={n} ip={clientAddress}
```

- `sub` is the internal numeric user ID (`-` if no usable identity could be established).
- `tool` is one of `search_items`, `get_item_image`, `search_boxes`, `get_box_contents`.
- `result` is `ok`, `denied` (failed the access gate), `rate_limited`, or `error` (bad input or internal failure).
- `hits` is the number of items/boxes returned (0 or 1 for an image).
- `ip` is the resolved client address, or `unknown` if it could not be resolved.

**The access token, the `Authorization` header and the per-user client ID never appear in this line, or anywhere
else in the logs** — this is intentional (see "Client-ID confidentiality" below) and was specifically verified in
review (the client ID is a credential the user must keep, and a leaked log file would otherwise leak it forever).
`McpClientController`'s response record has a redacted `toString()` for the same reason — Spring MVC's DEBUG
logging of controller method results could otherwise write it into the log even though nothing calls `log.debug`
on it directly.

Micrometer counter `boxpilot.mcp.tool.calls`, tagged by `tool` and `result` (same value sets as above). It exists
whenever the feature is enabled and needs no dedicated endpoint — it becomes visible the moment Actuator/
Prometheus scraping is active in the deployment, same as any other Micrometer counter in this app.

There is currently **no** audit line or counter for rejections at the HTTP layer before a tool call is reached —
e.g. a 429 from the IP-based rate limiters on `/oauth2/authorize`, `/oauth2/token` or failed bearer-token lookups
at `/mcp` (`McpIpRateLimitFilter`, `McpFailedTokenRateLimitFilter`). Those are logged by the filters themselves at
WARN but are not part of the `McpAuditLogger`/`McpToolMetrics` funnel. This was flagged in review as a known gap
(see Restrisiken below) rather than fixed in this sprint.

## Client-ID confidentiality and access-log handling

The per-user client ID is **confidential but not a secret in the password sense** — it identifies which BoxPilot
account a Claude connection is bound to, and is the only thing that stops a public shared-client phishing attack
(spec §2.1). BoxPilot itself never logs it. However:

- The client ID **appears in the query string of the `/oauth2/authorize` request** that Claude makes when
  connecting. If Traefik (or any reverse proxy in front of the backend) writes access logs with the full request
  URI including the query string, the client ID ends up there — and in any log shipping/retention downstream of
  that.
- It also ends up in the **user's own browser history** for the same reason. This is accepted as a residual risk
  (spec §8) mitigated only by "regenerate" if the user believes it leaked.

**Operator action:** configure Traefik (or whatever reverse proxy sits in front of the backend host) to omit the
query string from its access logs for this host, or to redact it. Traefik itself is not part of this repository
(see "Docker and Traefik" below), so the exact access-log directive is a Traefik-side configuration change outside
this repo — this document only flags the requirement. If access logs cannot be adjusted, treat them as
confidential data with the same handling as the database itself (see backup encryption below).

## Database backup encryption

Access and refresh tokens are stored **in plaintext** in `oauth2_authorization` (no hashing, per spec §4.3/§8,
an accepted risk for v1: opaque reference tokens have no other natural representation, and this database already
holds the entire inventory that MCP tokens grant read access to — nothing new is exposed to someone who already
has DB access). This makes the customary rule stronger here, not weaker: **backups of this database must be
encrypted at rest**, since a stolen backup contains live, usable bearer tokens until they expire or are cleaned
up (see nightly cleanup above; worst case is `max-connection-age`, 14 days, before a stale row is guaranteed
gone). There is no separate token-specific backup process — this is the same encryption-at-rest requirement that
already applies to the rest of the Postgres database, called out explicitly here because it is easy to overlook
for a table that "just" looks like an OAuth bookkeeping table.

## Residual risks (Restrisiken) an operator should know

From spec §8 (accepted, documented there in more detail) and points surfaced during this sprint's review rounds
(`.superpowers/sdd/2026-09-21-mcp-server-sprint/progress.md`):

- **Leaked client ID** — falls back to the pre-mitigation (v3) phishing exposure for that one user until
  regenerated. No system-side detection of a leak; it relies on the user noticing and asking for a new ID.
- **Prompt injection via free text and images** — item names, descriptions and images returned by the tools are
  untrusted user-entered data. `McpTextSanitizer` strips control/zero-width/bidi characters and truncates length,
  but a sufficiently motivated inventory entry could still attempt to manipulate an agent that also has other
  connectors with an outbound channel in the same chat.
- **No tenant separation** — BoxPilot has none to begin with; every MCP-enabled user sees the entire inventory,
  same as in the app itself.
- **Refresh-token theft without reuse detection** — Spring Authorization Server does not track token families, so
  a stolen (but not-yet-rotated) refresh token cannot be positively distinguished from a rotation by the
  legitimate client. Mitigated by rotation itself (`reuseRefreshTokens(false)`), the 7-day idle window, the
  14-day absolute connection age, and immediate effect of revoke/regenerate.
- **Plaintext tokens in the database** — see backup encryption above.
- **One client per user is enforced by an application-level lock, not a database constraint** — noted during the
  S9 review round. A future refactor that bypasses `McpClientProvisioningService` could in principle create a
  second client for the same user without the DB rejecting it. Not exploitable today (all paths go through the
  service), but worth remembering if the provisioning code is ever touched without full test coverage.
- **Failed-token-lookup rate limiting trades DB-cost protection for availability** — the S10 review round found
  that the fix for a limiter-bypass issue (I3) means every bearer-token guess now costs a database lookup rather
  than being short-circuited; a short-lived hashed negative cache was proposed as a follow-up but is **not**
  implemented in this sprint (open point, see below).
- **HTTP-layer rate-limit rejections (429s) are not audited or counted** — see the "no audit line" gap above;
  visibility into IP-based limiter activity currently means grepping WARN logs from `McpIpRateLimitFilter` /
  `McpFailedTokenRateLimitFilter` directly rather than a dashboard.
- **A 429 response from the rate limiters calls `response.reset()`, which discards previously set security
  headers** on that response — a minor defense-in-depth gap noted in the S10 re-review, not fixed this sprint.
- **Existing, unrelated to MCP but adjacent**: the public preview endpoint (`GET /api/v1/public/{uuid}`) still
  makes the box UUID on a printed QR label a de facto public access token; this is pre-existing behavior, not
  something MCP introduced, and MCP tools deliberately never return a box UUID for this reason (spec §3.3).

## Docker and Traefik

Traefik configuration itself lives **outside this repository** (see the main README's "Reverse Proxy (Traefik)"
section) — the points below are what that external Traefik setup needs to know, not something to change in this
repo's `docker-compose.yml` unless noted.

- The backend's existing Traefik router already matches on `Host(`${TRAEFIK_BACKEND_HOST}`)` with no further path
  restriction (see `docker-compose.yml`, `box-backend`/`box-backend-secure` routers), so `/mcp`, `/oauth2/**` and
  `/.well-known/**` are already reachable on the backend host once MCP is enabled — no additional Traefik router
  or path rule is required for this feature.
- Traefik must keep passing the client's `Host` through (its default, `passHostHeader: true`): `/mcp` only
  accepts the host of `BACKEND_URL`, and a rewritten `Host` is answered with 421.
- The frontend's nginx config only proxies `/api/`. It must **not** be extended to proxy `/mcp` or `/oauth2` —
  that routing intentionally stays backend-only (spec §5); do not "fix" this if it looks inconsistent.
- Traefik needs `forwardedHeaders.trustedIPs` (or the equivalent for the Traefik major version in use) configured
  to trust the addresses that actually sit between it and the backend container, matching the backend's own
  `TRUSTED_PROXY_PATTERN` (see the environment variable table below — that value is a **Java regular expression**
  matched by Tomcat's `RemoteIpValve`, not CIDR notation; the two settings describe the same trust boundary from
  either side and should be kept consistent by whoever administers Traefik). Without this, `X-Forwarded-For` is
  either not trusted (IP-based rate limits and audit `ip=` fields see the proxy's address, not the real client)
  or trusted from anywhere (spoofable).
- Redis and Postgres pinning/password (`REDIS_PASSWORD`, pinned `redis:7.4.11-alpine` image) were already handled
  in an earlier story of this sprint (S2) and required no further change for MCP specifically — mentioned here
  only so this document doesn't imply it's still open.
- Optional, operational only, not part of the design: a Traefik IP allowlist scoped to Anthropic's published
  egress ranges. Explicitly rejected as a *design* component in the spec (§5, §9) because the range is unverified
  and changes, and it would also block Claude Code and the MCP Inspector, which are legitimate manual-testing
  clients. If an operator wants this as a defense-in-depth measure anyway, it is their call, made outside this
  repo.

## Environment variables

New or changed for this feature. All are read by the backend; a `Y` in "In `.env.example`" means it was already
documented there or has just been added there as part of this story.

| Variable | Default | Required when | Purpose | In `.env.example` |
|---|---|---|---|---|
| `APP_MCP_ENABLED` | `false` | — | Master kill-switch for the whole MCP feature (`app.mcp.enabled`). | Y |
| `BACKEND_URL` | *(none)* | `APP_MCP_ENABLED=true` | Fixed OAuth issuer / resource identifier (`app.mcp.backend-url`). Startup fails without it (and without `https` in prod) when MCP is on. Never derived from the request or `X-Forwarded-*`. Also the DNS-rebinding allow-list of `/mcp`: the `Host` header must equal its host (with port, if it names a non-default one; `:443` on https and `:80` on http are ignored) or the call gets 421, and an `Origin`, if sent, must equal its origin or the call gets 403. | Y |
| `VITE_API_BASE_URL` | *(none, required)* | always | Base URL the SPA calls the backend on, baked into the frontend bundle at **build** time. **Must name the same origin as `BACKEND_URL`.** The backend sets the `mcp_authz` pending-authorization cookie and the OAuth issuer on `BACKEND_URL`, while the SPA builds the MCP "continue" URL and the connector URL shown in the "Claude-Verbindung" dialog from this value; if the two origins differ, the cookie is set on one origin and the continue-redirect points at the other, and the connect flow silently loops with no error. Since 0.2.0 the backend compares the two origins at startup (`McpStartupGuard`, via `app.mcp.spa-api-base-url`) and refuses to start when MCP is enabled and they differ; the value baked into an already-built frontend bundle cannot be checked, so rebuild the frontend whenever you change it. Changing it requires a frontend rebuild. | Y |
| `APP_MCP_CLEANUP_CRON` | `0 0 3 * * *` | optional | Cron schedule for the nightly authorization cleanup job. | Y |
| `APP_MCP_COOKIE_SAME_SITE` | *(blank — follows the session cookie)* | optional | `SameSite` attribute of the short-lived `mcp_authz` pending-authorization cookie (`app.mcp.cookie-same-site`). Blank means it follows `server.servlet.session.cookie.same-site` (`none` in `application-prod.yml`), because the cookie has to travel on exactly the requests the session cookie travels on. Set it to `Lax` where frontend and backend share a site. | Y |
| `APP_MCP_CALLS_PER_MINUTE` | `60` | optional | Per-subject rate limit on `/mcp` tool calls. | Y |
| `APP_MCP_IMAGE_CALLS_PER_DAY` | `200` | optional | Per-subject daily quota on `get_item_image` calls (the expensive, large-payload tool). | Y |
| `APP_MCP_AUTHORIZATION_REQUESTS_PER_MINUTE` | `30` | optional | Per-IP rate limit on `/oauth2/authorize` and `/oauth2/token`. Only meaningful if `TRUSTED_PROXY_PATTERN`/M4 is correctly configured — otherwise it rate-limits the proxy's address, not the caller's. | Y |
| `APP_MCP_FAILED_TOKEN_LOOKUPS_PER_MINUTE` | `30` | optional | Per-IP limit on failed bearer-token lookups at `/mcp` (every failed lookup costs a DB query). Same caveat as above. | Y |
| `TRUSTED_PROXY_PATTERN` | private-address-space regex (see `application-prod.yml`) | optional, recommended in prod | **Java regular expression** (not CIDR) matched by Tomcat's `RemoteIpValve` against the immediate peer address, to decide whether to trust its `X-Forwarded-*` headers. Underpins the two IP-based limits above, the login throttling of `POST /api/auth/login` and `POST /api/v1/auth/magic-login`, and the `ip=` field in the MCP audit log. Renamed from the originally spec'd `TRUSTED_PROXY_CIDR` during implementation review because the underlying mechanism is regex-based, not CIDR-based. | Y |
| `REDIS_PASSWORD` | *(none, required)* | always in prod | Redis `requirepass` and the backend's session-store credential. Predates MCP (S2 hardening) but is a hard prerequisite for the app to start at all in `prod`. | Y |
| `ADMIN_PASSWORD` | *(none in prod; `admin` only in `dev`)* | always in prod | Bootstrap admin account password. Startup fails in `prod` if empty, literally `admin` or shorter than 12 characters, and also if an existing admin account's password hash still matches `admin`. Predates MCP (S2 hardening), listed here because it gates the Admin Panel screen used to grant `mcp_allowed`. | Y |
| `FRONTEND_URL` | *(none, required)* | always | Base URL of the SPA. Already required pre-MCP; now additionally the base of magic links and the redirect target after the pending-authorization cookie flow (spec §4.1/§4.4). | Y |
| `MAGIC_LINK_VALID_HOURS` | `24` | optional | Maximum lifetime of a magic login link (hard cap enforced since the S2/S3 magic-link hardening fixes; the previous unbounded value is no longer accepted). | Y |

`app.mcp.max-connection-age` (default `14d`) and `app.mcp.redirect-uris` (default: Claude's
`https://claude.ai/api/mcp/auth_callback`) are set in `application.yml` directly rather than via env vars — there
was no operational need identified to override either per deployment, so they were left as plain YAML config
rather than added to `.env.example`. Change them in `backend/src/main/resources/application.yml` if a deployment
ever needs a different value.

`.env.example` in the repo root has been updated to include the MCP-specific variables above (`APP_MCP_ENABLED`,
`BACKEND_URL`, `APP_MCP_CLEANUP_CRON`, `APP_MCP_COOKIE_SAME_SITE` and the four rate-limit knobs,
`TRUSTED_PROXY_PATTERN`) alongside the pre-existing entries; `REDIS_PASSWORD`, `ADMIN_PASSWORD`, `FRONTEND_URL`,
`VITE_API_BASE_URL` and `MAGIC_LINK_VALID_HOURS` were already present there from earlier stories. `BACKEND_URL` and
`VITE_API_BASE_URL` now ship with the same example origin and carry a comment on both entries stating that they
must stay on the same origin.

## Open points (not covered by this sprint, needed before/at go-live)

These are explicitly out of scope for the implementation stories (S1–S12) and need a human decision or a manual
step before or at go-live. Source: the sprint plan's "Out of scope this sprint" section
(`docs/superpowers/plans/2026-09-21-mcp-server-sprint.md`), spec §7 Phase 0 spike items, spec §8, and the sprint
ledger (`.superpowers/sdd/2026-09-21-mcp-server-sprint/progress.md`).

1. **Manual MCP Inspector / Claude Free verification** — the automated test suite (through S12, including the
   scripted `McpEndToEndFlowTest`) covers the protocol and security behavior end to end with MockMvc/
   TestRestTemplate/Testcontainers, but neither the actual MCP Inspector tool nor a real Claude Free/Pro account
   has been driven against a live deployment — S12 could not do this itself (no browser, no claude.ai account, no
   public HTTPS origin in that environment). Whoever runs this before go-live needs:

   **Prerequisites** — note that `app.mcp.redirect-uris` is **YAML-only**: unlike the variables in the table
   above it cannot be overridden by an environment variable, so adding the loopback callback below means editing
   `backend/src/main/resources/application.yml` (or `application-prod.yml`) and rebuilding and redeploying the
   backend image, not just changing `.env`. Remove the loopback entry again after the verification run.
   - Backend reachable over **public HTTPS** (Claude will not call a private host); `BACKEND_URL` set to exactly
     that origin, Traefik forwarding `X-Forwarded-*`.
   - `app.mcp.enabled=true`, `app.mcp.redirect-uris` containing `https://claude.ai/api/mcp/auth_callback` and
     `http://127.0.0.1:41000/callback`.
   - A test user with `mcp_allowed` switched on in the admin panel, and at least one box with items and one item
     with a photo.
   - Traefik access logs configured without query strings (see "Client-ID confidentiality and access-log
     handling" above — the client ID appears in the authorize query string).

   **A. MCP Inspector**
   1. `npx @modelcontextprotocol/inspector` (Node 18+) — opens the inspector UI in the browser.
   2. Transport type **Streamable HTTP**, URL `https://<backend>/mcp`.
   3. In **Authentication / OAuth**, choose OAuth 2.1 and enter the client ID from the BoxPilot "Claude-Verbindung"
      dialog (`POST /api/v1/me/mcp-client`). Leave the client secret empty (public client) and let the inspector
      use PKCE S256 and its own loopback redirect — if it can't be pinned to port 41000, either register its
      actual loopback redirect URI first, or use the pre-registered `http://127.0.0.1:41000/callback`.
   4. Press **Connect** and verify: the browser lands on the BoxPilot login page, then the consent page shows
      *your* user name, scope `mcp:read` and the redirect host; approving returns to the inspector connected.
   5. Verify **Tools** lists exactly `search_items`, `get_item_image`, `search_boxes`, `get_box_contents`, each
      flagged read-only.
   6. Run each tool against real data (a known item name, a room, a known box number, an `itemId` from a search
      result) and confirm `get_item_image` actually renders the photo in the inspector's result pane.
   7. Check the payloads: no `boxUuid`, `imageToken` or image URL should appear anywhere.
   8. Negative checks: another user's client ID must be refused at authorize; switching `mcp_allowed` off must
      fail the next tool call; switching it back on must require a fresh connection (client and authorizations
      deleted, not just re-enabled).

   **B. Claude Free, custom connector**
   1. claude.ai → Settings → Connectors → **Add custom connector**, name `BoxPilot`, URL `https://<backend>/mcp`,
      client ID from the BoxPilot dialog under advanced settings, no client secret.
   2. Connect — the same login/consent pages must appear and the connector must end up "Connected".
   3. In a chat, enable the connector and ask for inventory by room and for a photo of a specific item; confirm
      all four tools get offered, the answers reflect real inventory, and the photo displays.
   4. Repeat on the Claude **mobile app** with the same account specifically to check whether image content
      renders there (spec §7 Phase 0.5).
   5. Leave the connection idle past the 30-minute access-token lifetime, then use it again — the refresh must
      happen transparently. Claude has a known bug with stored client credentials surviving a refresh
      (issue #344, spec §7 Phase 0.1) — watch for an unexpected re-consent prompt.
   6. Press "Alle Verbindungen widerrufen" in the BoxPilot dialog and confirm the connector stops working with no
      further BoxPilot-side action.

   **Spring Boot 4 addition, NOT YET RUN:** since the upgrade `/mcp` refuses a `Host` that differs from `BACKEND_URL`
   (421) and an `Origin` that differs from its origin (403); requests without `Origin` pass. This was verified with
   scripted clients and the MCP Inspector CLI only. Nobody has yet confirmed with a real claude.ai connector (web and
   mobile) which `Host` and `Origin` it sends. If Claude.ai sends an `Origin` such as `https://claude.ai`, the
   connector will be refused with 403 and the check needs a change. While running A and B, watch the backend log and
   the proxy access log for 403/421 on `/mcp` and note the request's `Host` and `Origin`.

   This is the only way to confirm things a real client alone can show: whether Claude sends `scope`/`resource`
   on authorize, the refresh/credential-storage behavior above, and mobile image rendering. See S12's report
   (`.superpowers/sdd/2026-09-21-mcp-server-sprint/S12-report.md`, section 4) for the original write-up.
2. **Traefik access logs must exclude the query string** — the client ID travels in the `/oauth2/authorize`
   query string (see "Client-ID confidentiality and access-log handling" above). This sprint's stories only
   verified that BoxPilot itself never logs the client ID; whoever operates the reverse proxy still needs to
   confirm Traefik's access-log directive for this host omits or redacts the query string (or, failing that,
   treat those logs as confidential with the same handling as the database — see "Database backup encryption"
   above) before go-live. Flagged again during S12's QA pass as still open.
3. **ChatGPT (Developer Mode) support** — explicitly out of scope for v1 (spec §1, §7 Phase 0.5); not tested at
   all with this implementation.
4. **Spring Boot 3.5 support-status decision** — *resolved.* Boot 3.5's OSS support ended on 2026-06-30. The
   backend now runs on Spring Boot 4.1.x (Spring Security 7, Spring AI 2, MCP SDK 2), whose OSS support runs until
   2027-07-31 (endoflife.date). Upgrade and rollback notes are in the main README, "Upgrading from the Spring Boot
   3.5 build"; the evidence is in `docs/superpowers/plans/artifacts/verification-boot4.md`.
5. **Staging `flyway validate`** — `validate-on-migrate` stays `false` in `application-prod.yml`. Before it is
   ever turned on, a `flyway validate` run against a copy of the real production migration history on staging is
   required (spec §4.3); this sprint added the new MCP migrations but did not perform or automate that
   validation run.
6. **Remaining §4.4 "Phase 4 and own tickets" items**, not bundled into this sprint's stories:
   - **M3** — session invalidation on role change or user deactivation: done (`SessionRevalidationFilter`, and since
     0.2.0 also after a password reset). The 30-minute idle default of `@EnableRedisHttpSession` applies.
   - **Container hardening** — done in 0.2.0: the backend runs non-root (uid 10001) with a read-only root filesystem and
     `cap_drop: ALL`; the frontend drops all capabilities except those nginx needs (see the 0.2.0 release notes).
   - **Unified login error messages** — locked/disabled/wrong-password are still distinguishable in the login
     failure handler (`OAuth2SecurityConfig`), an account-enumeration nicety, not fixed this sprint.
   - **Configurable cookie `SameSite` for the *session* cookie** — done in 0.2.0: `SESSION_COOKIE_SAME_SITE`
     (default `none`; `lax` where frontend and backend share a site). The MCP `mcp_authz` cookie has its own
     `APP_MCP_COOKIE_SAME_SITE` (see the environment variable table above) and only falls back to the session
     cookie's value when that is left blank.
   - **Global LIKE-escaping audit** — spec §3.3 assumes Spring Data's derived `...ContainingIgnoreCase` methods
     escape `%`/`_` themselves; this was accepted based on framework behavior but a systematic check across all
     search paths (not just the new MCP ones) was not performed as a separate ticket.
7. **Negative cache for failed token lookups** — a follow-up noted during the S10 review round: the fix that
   closed a rate-limit bypass (I3) made every invalid bearer token cost a real database lookup instead of being
   cheaply rejected. A short-lived hashed negative cache was suggested as a way to get the DB-cost protection
   back without reintroducing the bypass, but it is not built in this sprint.
8. **HTTP-layer rate-limit observability** — 429 responses from the IP-based limiters are not currently audited
   or counted through `McpAuditLogger`/`McpToolMetrics` (see "audit log" section above); adding that is not
   scheduled work, just a known gap.
9. **Data-model ticket: `Box.description` is a Postgres large object** — the field is mapped as a JPA
   `@Lob String`, which on Postgres becomes a large object (OID) rather than a `TEXT` column. A SQL
   `LIKE`/`ILIKE` therefore cannot be used on it, which is why the MCP box-description search in
   `DefaultMcpReadService` loads the candidate boxes and matches the description **in memory**. That is fine at
   household-inventory sizes and was accepted for this sprint, but it does not scale: the fix is to map the
   column properly as `TEXT` (`@Column(columnDefinition = "text")`, no `@Lob`) plus a Flyway data migration that
   moves the existing large objects into the column, after which the search becomes an ordinary SQL query. Own
   ticket, not MCP-specific — the same `@Lob` mapping affects every other read of `Box.description`.
10. **Test hygiene: fixed box numbers in `McpEndToEndFlowTest`** — the scripted end-to-end flow uses hard-coded
    box numbers (8120/8121) rather than generated ones. Against the ephemeral Testcontainers database this is
    safe, but a crashed run that leaves rows behind, or a future parallel/shared-database setup, could collide
    or make cleanup order matter. Low-priority follow-up: generate the numbers per run (or assert-and-clean
    before creating them).
11. **`mcpAllowed` in `/api/v1/me` does not reflect a temporary lock** — `McpUserAllowanceProbe` reports the
    stored `users.mcp_allowed` flag only, while the actual gate (`McpAccessPolicy`) additionally denies a
    temporarily locked account. Since 0.2.0 no new locks are created and V15 cleared the old ones, so this can only
    occur for a lock written by hand. A locked user therefore still sees the "Claude-Verbindung" dialog and gets a
    bare `403` when a tool call or an authorize request is attempted. This is deliberate and documented in a
    comment on the probe: a lock is temporary (it expires on its own) and the dialog's own maintenance actions
    remain legitimate while it lasts. The residual rough edge is purely cosmetic — the dialog's error states are
    not differentiated by HTTP status, so a `403` reads like any other failure. Turning that into an explicit
    "not currently available" message is an optional UX follow-up, not a security matter: enforcement is correct
    and fail-closed either way.
