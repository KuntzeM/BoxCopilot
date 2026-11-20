# Security hardening (2026-11) - breaking operational changes

Check these before upgrading:

- **ACTION REQUIRED: run `flyway repair` once before upgrading.** `flyway.validate-on-migrate` is now `true` in
  production. Migrations V6, V9 and V10 were edited after release (see `git log` for those files), so a database that
  applied an earlier version fails the checksum check at startup. Run `flyway repair` (or equivalent: update
  `flyway_schema_history.checksum` for those versions) against the database first, then start the new version.

- **`CSP_MODE` now defaults to `enforce`** (was `report`). Set `CSP_MODE=report` temporarily if a custom setup loads
  resources the policy blocks.
- **Proxy trust.** The backend's direct peer for API traffic is Traefik on the external `traefik-proxy` network (the
  backend has its own Traefik router, see `docker-compose.yml`), not only the frontend nginx. Set
  `TRUSTED_PROXY_PATTERN` (a Java regex, not CIDR) to match the `traefik-proxy` subnet; the default is
  `172.16.0.0/12` plus loopback. The frontend nginx overwrites `X-Forwarded-For` using `REAL_IP_FROM`, which must
  match the Traefik/edge peer nginx sees (default `172.16.0.0/12`). If your `traefik-proxy` network is outside that
  range (e.g. `10.x`, `192.168.x`), set both. A wrong value makes every client look like the proxy, so **all clients
  share one login/MCP throttle bucket**.
- **`X-Forwarded-Proto` is now passed through** by nginx (http/https only), so the backend sees `https` behind
  Traefik.
- **Migration V17 deletes all existing magic-login tokens** (tokens are now stored as SHA-256 hashes). Links already
  issued stop working; generate new ones.
- **Public image URLs** are cached `public, max-age=3600` (no longer 1 year `immutable`), and an item's image token
  rotates when its image is deleted or replaced, so old shared image URLs stop working. The token also rotates when an item is moved, so image links previously
  shared from a box preview stop working after a move.
- **StartupGuard (prod profile)** now refuses to start with the `dev` profile active, a file-based H2 datasource, or
  `ADMIN_PASSWORD=admin`. Production also runs `flyway.validate-on-migrate: true` and `ddl-auto: none`.
- The last enabled admin can no longer be demoted, disabled or deleted; Box description (5000 chars),
  bulk-move item ids (500) and the item search query (100 chars; longer returns HTTP 400) and search result count (200)
  are capped.
- CI/Dockerfile use plain `npm ci` (no `npm install` fallback); the lockfile must be in sync.
