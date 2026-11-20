# Security

## Login throttling

Failed logins are counted per client IP and per IP and username. By default an IP may fail 10 logins in 15 minutes and one IP and username pair 5 (`APP_LOGIN_THROTTLE_IP_LIMIT`, `APP_LOGIN_THROTTLE_IP_USER_LIMIT`, `APP_LOGIN_THROTTLE_WINDOW`).

After that, `POST /api/auth/login` and `POST /api/v1/auth/magic-login` answer 429 with a `Retry-After` header, before any password check. Failed magic-link redemptions count against the IP only.

A blocked IP cannot log in even with the correct password until the window ends, so people behind one shared address (NAT) share the quota. Accounts are never locked, so nobody can lock someone else out by guessing their username.

The counters live in the backend process. They reset on restart and are not shared between replicas, so run a single backend replica.

## Trusted proxies

The client IP comes from `X-Forwarded-For`, which Tomcat trusts only from addresses matching `TRUSTED_PROXY_PATTERN`. The value is a Java regular expression, not CIDR. The default covers Docker's default address pool (172.16.0.0/12) and loopback.

The app's direct peer is Traefik on the external `traefik-proxy` network, so the pattern must match that subnet. A blank value counts as unset. At startup the backend logs the pattern in effect (`Trusted proxy pattern (default|custom from TRUSTED_PROXY_PATTERN): ...`).

If your proxy is outside the range or the pattern is wrong, all clients look like the proxy and share one throttle counter, so a few failed logins block everybody.

## Sessions and passwords

Setting a user's password in the Admin Panel ends all of that user's sessions, invalidates their magic links and revokes their MCP access. Resetting your own password logs you out.

The session holds no password hash. Every authenticated request re-reads the user from the database to check that the session is still valid.

`SESSION_COOKIE_SAME_SITE` defaults to `lax` because SPA and API share one origin. `strict` breaks the OIDC redirect back to the app.

## Security headers

The app sends:

- `Content-Security-Policy` with `'self'` only. It is enforced by default; `CSP_MODE=report` switches to report-only while you debug.
- `Strict-Transport-Security`, controlled by `HSTS_ENABLED`. It is on by default in `docker-compose.yml`; set `false` for plain http.
- `X-Frame-Options: DENY`
- `X-Content-Type-Options: nosniff`
- `Referrer-Policy: no-referrer`

`index.html` is served with `Cache-Control: no-cache`. The hashed bundles under `/assets/` are cached for a year and marked `immutable`.

## Containers

The app runs as uid 10001 with a read-only root filesystem (`/tmp` is a tmpfs), no capabilities and a memory limit. The postgres container does not receive application secrets it does not need.

## Uploads

Images are limited to 25 megapixels. Only a few are decoded at once (`APP_IMAGE_MAX_CONCURRENT_UPLOADS`, default 2); further uploads wait briefly and then get 503 with `Retry-After`. The backend refuses to start if the image directory is not writable.

## Public links

Every box has a public UUID link and item images use separate tokens. Regenerating the link of a box rotates both, so old links and printed QR labels stop working.

## MCP

The MCP OAuth issuer and resource identifier is `APP_URL`. With `APP_MCP_ENABLED=true` it must be https in production. See [MCP operations](ops/mcp-server.md).
