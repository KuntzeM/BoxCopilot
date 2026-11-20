# Single image: frontend and backend in one container - breaking operational changes

BoxPilot now ships as **one Docker image**. The root `Dockerfile` builds the React SPA with Node, copies the bundle
into the Spring Boot jar (`classpath:/static`) and runs it on a JRE as uid 10001. Spring Boot serves the SPA, the API,
the OAuth login, the MCP endpoints and the security headers on **one origin**. The nginx frontend container is gone.

## What changes

- **One service.** `docker-compose.yml` has `postgres`, `redis` and `app` (was `backend` + `frontend`). The app listens
  on port 8080 and has one Traefik router (`box` / `box-secure`) on `Host(${TRAEFIK_HOST})`, with the same
  HTTP -> HTTPS redirect and certificate resolver as before.
- **One URL variable.** `APP_URL` (public origin, `scheme://host[:port]`, no path, no trailing slash) replaces
  `FRONTEND_URL`, `VITE_API_BASE_URL` and `BACKEND_URL`. It is the base of the OAuth redirect URI, magic links, the
  logout redirect and the MCP OAuth issuer. The SPA uses relative URLs only; nothing is baked in at build time.
- **Removed variables:** `FRONTEND_URL`, `VITE_API_BASE_URL`, `BACKEND_URL`, `TRAEFIK_FRONTEND_HOST`,
  `TRAEFIK_BACKEND_HOST`, `REAL_IP_FROM`, `BACKEND_PORT`. New: `APP_URL`, `TRAEFIK_HOST`.
- **Security headers come from Spring.** `CSP_MODE` (`enforce` default, `report` for Report-Only) and `HSTS_ENABLED`
  (`false` in the app, `true` in `docker-compose.yml`) are now read by the app. The CSP allows `'self'` only, since
  there is no second origin any more. `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff` and
  `Referrer-Policy: no-referrer` are unchanged.
- **Session cookie `SameSite` defaults to `lax`** (was `none`). `SESSION_COOKIE_SAME_SITE` still overrides it.
- **Proxy trust.** Traefik is the app's only peer. `TRUSTED_PROXY_PATTERN` (Java regex, not CIDR) must match the
  `traefik-proxy` subnet, as before; `REAL_IP_FROM` no longer exists.
- **Memory.** The app container keeps `mem_limit: 1g`; the former frontend container's 128 MB are freed.

## Upgrade steps

1. Back up database and images ([`docs/ops/backup-restore.md`](../ops/backup-restore.md)).
2. In `.env`: set `APP_URL` to the public origin users open (normally the former `FRONTEND_URL`), set `TRAEFIK_HOST`
   to its host, and delete `FRONTEND_URL`, `VITE_API_BASE_URL`, `BACKEND_URL`, `TRAEFIK_FRONTEND_HOST`,
   `TRAEFIK_BACKEND_HOST`, `REAL_IP_FROM`. Remove `SESSION_COOKIE_SAME_SITE=none` unless you need it.
3. In Nextcloud, change the OAuth client's redirect URI to `<APP_URL>/login/oauth2/code/nextcloud`.
4. With MCP enabled: the connector URL becomes `<APP_URL>/mcp`. Existing Claude connectors that point at the former
   backend host must be removed and added again.
5. Stop the old stack (`docker compose down`, which removes the old `backend` and `frontend` containers), then
   `docker compose up -d --build`. The bind mounts `logs/`, `images/` and `pgdata/` are unchanged; the uid stays 10001.
6. If the former API host should keep working for old QR labels or bookmarks, add a Traefik redirect from it to
   `APP_URL`; public links (`/public/{uuid}`) always used the frontend host and keep working.

## Rollback

Check out the previous release, restore the previous `.env` (and the Nextcloud redirect URI), then
`docker compose up -d --build`. There is no database migration in this change.

## Release images

The release workflow now publishes a single image `ghcr.io/<owner>/<repo>` (previously `-backend` and `-frontend`).
