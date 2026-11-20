# Reverse proxy: Traefik


**Important:** BoxCopilot requires Traefik to be running **externally** before starting the application. The services connect to an existing Traefik instance via the `traefik-proxy` Docker network.

## Complete Traefik Setup Example

If you don't have Traefik running yet, create a separate `traefik-compose.yml`:

```yaml
version: '3.8'

services:
  traefik:
    image: traefik:v2.10
    container_name: traefik
    restart: unless-stopped
    security_opt:
      - no-new-privileges:true
    networks:
      - traefik-proxy
    ports:
      - "80:80"
      - "443:443"
    environment:
      - CLOUDFLARE_EMAIL=${CLOUDFLARE_EMAIL}     # Optional: for DNS challenge
      - CLOUDFLARE_API_KEY=${CLOUDFLARE_API_KEY}   # Optional: for DNS challenge
    volumes:
      - /etc/localtime:/etc/localtime:ro
      - /var/run/docker.sock:/var/run/docker.sock:ro
      - ./traefik/traefik.yml:/traefik.yml:ro
      - ./traefik/acme.json:/acme.json
      - ./traefik/config.yml:/config.yml:ro
    labels:
      - "traefik.enable=true"
      - "traefik.http.routers.traefik.entrypoints=http"
      - "traefik.http.routers.traefik.rule=Host(`traefik.example.com`)"
      - "traefik.http.middlewares.traefik-https-redirect.redirectscheme.scheme=https"
      - "traefik.http.middlewares.sslheader.headers.customrequestheaders.X-Forwarded-Proto=https"
      - "traefik.http.routers.traefik.middlewares=traefik-https-redirect"
      - "traefik.http.routers.traefik-secure.entrypoints=https"
      - "traefik.http.routers.traefik-secure.rule=Host(`traefik.example.com`)"
      - "traefik.http.routers.traefik-secure.tls=true"
      - "traefik.http.routers.traefik-secure.tls.certresolver=http"
      - "traefik.http.routers.traefik-secure.service=api@internal"

networks:
  traefik-proxy:
    # fixed name: BoxCopilot's docker-compose.yml joins this network as external "traefik-proxy"
    name: traefik-proxy
```

The `traefik.example.com` dashboard router above has no authentication middleware. Remove it (and `api.dashboard`) or protect it before exposing Traefik to the internet.

Create `traefik/traefik.yml`:
```yaml
api:
  dashboard: true
  debug: true

entryPoints:
  http:
    address: ":80"
    http:
      redirections:
        entryPoint:
          to: https
          scheme: https
  https:
    address: ":443"

serversTransport:
  insecureSkipVerify: true

providers:
  docker:
    endpoint: "unix:///var/run/docker.sock"
    exposedByDefault: false
  file:
    filename: /config.yml

certificatesResolvers:
  # The name must match the tls.certresolver label in docker-compose.yml ("http").
  http:
    acme:
      email: your-email@example.com
      storage: acme.json
      # Use HTTP challenge for Let's Encrypt:
      # httpChallenge:
      #   entryPoint: http
      # Or DNS challenge (example with Cloudflare):
      dnsChallenge:
        provider: cloudflare
        resolvers:
          - "1.1.1.1:53"
          - "1.0.0.1:53"
```

Create empty `traefik/acme.json` with correct permissions:
```bash
mkdir -p traefik
touch traefik/acme.json
chmod 600 traefik/acme.json
```

Start Traefik:
```bash
docker-compose -f traefik-compose.yml up -d
```

Verify the `traefik-proxy` network exists:
```bash
docker network ls | grep traefik-proxy
```

Then start BoxCopilot services with the main `docker-compose.yml`.

## Network Configuration

BoxCopilot's `docker-compose.yml` connects to the external `traefik-proxy` network:
```yaml
networks:
  traefik-proxy:
    external: true
```

The single `app` service (Spring Boot serving the SPA, the API, OAuth and `/mcp` on port 8080) carries the Traefik labels
(one router on `Host(${TRAEFIK_HOST})`, certificate resolver `http`, entrypoints `http` and `https`) for:
- HTTP → HTTPS redirect
- TLS certificate provisioning (Let's Encrypt)
- Host-based routing

`TRAEFIK_HOST` must be the host of `APP_URL`.

Traefik forwards `X-Forwarded-For`; the app uses it for login throttling only for requests that arrive from an address matching `TRUSTED_PROXY_PATTERN` (default: Docker's default address pool 172.16.0.0/12 and loopback; set it if your compose network uses another range). Check the pattern if Traefik runs on a different network, see [Security](../../README.md#security).

