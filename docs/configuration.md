# Configuration

## Profiles

| Profile | Database | Sessions | Use |
|---|---|---|---|
| `dev` | H2 file database | JDBC | Local development; OIDC enabled, dual authentication |
| `prod` | PostgreSQL with Flyway | Redis | Production; OIDC enabled, dual authentication, startup guards |
| `test` | H2 in memory | n/a | Unit and controller tests; security disabled |

The profile settings are in [`application-*.yml`](../backend/src/main/resources/). A fourth profile, `mcp-test`, layers on the test profile for real MCP chains and uses PostgreSQL Testcontainers.

In `prod` the backend lists all missing required variables in one startup message.

## Environment variables

[`.env.example`](../.env.example) lists every variable with comments. The ones you will touch first:

| Variable | Purpose |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` or `prod` |
| `APP_URL` | Public origin of the application (`https://host[:port]`, no path, no trailing slash). Used for the OAuth redirect URI, magic links, the logout redirect and the MCP issuer |
| `CLIENT_ID`, `CLIENT_SECRET` | OAuth2/OIDC client registered in your Nextcloud |
| `NEXTCLOUD_URL`, `NEXTCLOUD_LOGOUT_URL` | OIDC issuer and logout URL |
| `ADMIN_USERNAME`, `ADMIN_PASSWORD`, `ADMIN_NAME` | Bootstrap admin. In `prod` the password needs at least 12 characters and cannot be `admin` |
| `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB` | Database (`prod`) |
| `REDIS_PASSWORD` | Redis password and session store (`prod`, required) |
| `APP_IMAGE_STORAGE_PATH` | Image directory. `./data/images` in development, `/var/boxcopilot/images` in production |
| `TRAEFIK_HOST` | Host of the Traefik router in `docker-compose.yml`, the host of `APP_URL` |

## Optional settings

| Variable | Default | Purpose |
|---|---|---|
| `APP_OIDC_AUTO_PROVISION` | `false` | `false`: a first-time Nextcloud login creates a disabled account that an admin must enable |
| `MAGIC_LINK_VALID_HOURS` | `24` | Default validity of magic links |
| `CSP_MODE` | `enforce` | `report` sends Content-Security-Policy-Report-Only, for debugging |
| `HSTS_ENABLED` | `false` in the app, `true` in `docker-compose.yml` | Send `Strict-Transport-Security` |
| `SESSION_COOKIE_SAME_SITE` | `lax` | Do not use `strict`: it breaks the OIDC redirect back to the app |
| `TRUSTED_PROXY_PATTERN` | Docker's default pool plus loopback | Java regex of the proxies whose `X-Forwarded-*` headers are trusted |
| `APP_LOGIN_THROTTLE_IP_LIMIT` | `10` | Failed logins per IP in the window |
| `APP_LOGIN_THROTTLE_IP_USER_LIMIT` | `5` | Failed logins per IP and username in the window |
| `APP_LOGIN_THROTTLE_WINDOW` | `15m` | Length of the throttle window |
| `APP_IMAGE_MAX_CONCURRENT_UPLOADS` | `2` | Image uploads decoded at the same time |
| `APP_MCP_ENABLED` | `false` | Claude / MCP server, see [Claude and MCP](claude-mcp.md) |

The remaining MCP variables (cleanup schedule, cookie, rate limits) are documented in the [MCP operations guide](ops/mcp-server.md#environment-variables).

## Development defaults

With the `dev` profile the bootstrap admin is `admin` / `admin` and data goes to `./data/boxcopilot-dev` and `./data/images`. The dev profile still resolves the OIDC issuer at startup, so `NEXTCLOUD_URL` must point to a reachable OIDC provider, see [Development](development.md).
