# Security hardening (2026-11) - breaking operational changes

See `docs/security-audit-2026-11-19.md` for the findings. Check these before upgrading:

- **`CSP_MODE` now defaults to `enforce`** (was `report`). Set `CSP_MODE=report` temporarily if a custom setup loads
  resources the policy blocks.
- **Proxy trust.** The nginx frontend now overwrites `X-Forwarded-For` with the real client address, resolved through
  `REAL_IP_FROM` (default `172.16.0.0/12`). The backend default `TRUSTED_PROXY_PATTERN` is `172.16.0.0/12` plus
  loopback instead of all private ranges. If your Docker/Traefik network is outside `172.16.0.0/12` (e.g. `10.x`,
  `192.168.x`), set both `REAL_IP_FROM` and `TRUSTED_PROXY_PATTERN`, otherwise all clients share one login/MCP
  throttle counter.
- **Migration V17 deletes all existing magic-login tokens** (tokens are now stored as SHA-256 hashes). Links already
  issued stop working; generate new ones.
- **Public image URLs** are cached `public, max-age=3600` (no longer 1 year `immutable`), and an item's image token
  rotates when its image is deleted or replaced, so old shared image URLs stop working.
- **StartupGuard (prod profile)** now refuses to start with the `dev` profile active, a file-based H2 datasource, or
  `ADMIN_PASSWORD=admin`. Production also runs `flyway.validate-on-migrate: true` and `ddl-auto: none`.
- The last enabled admin can no longer be demoted, disabled or deleted; name/description fields, bulk item id lists and
  page sizes are capped.
- CI/Dockerfile use plain `npm ci` (no `npm install` fallback); the lockfile must be in sync.
