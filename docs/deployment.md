# Production deployment

## Prerequisites

- Docker and Docker Compose
- A Traefik reverse proxy that runs outside this compose project
- A Nextcloud instance for OIDC sign-in

## Architecture

The image is built from the root [`Dockerfile`](../Dockerfile). It builds the frontend with Node, copies the bundle into the Spring Boot jar and runs it on a JRE as uid 10001. There is one application container (`app`). Spring Boot serves the SPA, the API, the OAuth endpoints and `/mcp` on port 8080, all on the origin `APP_URL`. PostgreSQL and Redis run as separate services in the same compose file.

## Setup

### 1. Create the environment file

Create `.env` next to `docker-compose.yml`:

```bash
# Database
POSTGRES_USER=boxuser
POSTGRES_PASSWORD=secure-password-here
POSTGRES_DB=boxcopilot

# Redis (sessions) and the bootstrap admin; ADMIN_PASSWORD needs at least 12 characters
REDIS_PASSWORD=generate-with-openssl-rand-base64-24
ADMIN_PASSWORD=generate-with-openssl-rand-base64-24

# Public origin: SPA, API, OAuth callback and MCP share it
APP_URL=https://boxes.example.com

# OAuth2/OIDC (Nextcloud)
CLIENT_ID=your-nextcloud-oauth-client-id
CLIENT_SECRET=your-nextcloud-oauth-client-secret
NEXTCLOUD_URL=https://cloud.example.com
NEXTCLOUD_LOGOUT_URL=https://cloud.example.com/index.php/logout

# Traefik host (the host of APP_URL)
TRAEFIK_HOST=boxes.example.com
```

[`.env.example`](../.env.example) lists every variable, including the optional ones. See [Configuration](configuration.md).

### 2. Prepare the data directories

The app runs as non-root uid 10001. Create both directories once, before the first start:

```bash
mkdir -p logs images && sudo chown -R 10001:10001 logs images
```

Without this the container exits. The log then shows `Permission denied` for `/app/logs/boxcopilot.log` or the image directory error.

### 3. Start the services

```bash
docker compose up -d --build
```

`docker compose ps` shows `app` as healthy once `GET /actuator/health` reports `UP`.

Back up the database and the `images` directory together, see [Backup and restore](ops/backup-restore.md).

## Nextcloud OIDC setup

Register an OAuth2/OIDC client in Nextcloud with these settings:

- Redirect URI: `APP_URL` + `/login/oauth2/code/nextcloud`, for example `https://boxes.example.com/login/oauth2/code/nextcloud`
- Grant type: Authorization Code
- Scopes: `openid`, `profile`, `email`

Put the generated client ID and client secret into `.env` as `CLIENT_ID` and `CLIENT_SECRET`.

By default a first-time Nextcloud login creates a disabled account. An admin enables it in Administration. Set `APP_OIDC_AUTO_PROVISION=true` to enable new accounts automatically.

## Reverse proxy (Traefik)

BoxCopilot needs an external Traefik instance that shares the `traefik-proxy` Docker network. The `app` service carries the Traefik labels: one router on `TRAEFIK_HOST`, an HTTP to HTTPS redirect and Let's Encrypt. A complete example and the network configuration are in [Traefik](ops/traefik.md).

Traefik forwards `X-Forwarded-For`. The backend trusts it only from addresses matching `TRUSTED_PROXY_PATTERN`, see [Security](security.md#trusted-proxies).

## First sign-in

Sign in with `ADMIN_USERNAME` (default `admin`) and the `ADMIN_PASSWORD` from `.env`. Create further users in Administration, or let people sign in with Nextcloud.
