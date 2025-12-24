# BoxCopilot

**Keep your moving organized.** BoxCopilot helps families stay sane during moves by tracking what's packed in each box and where it should go. No more opening 20 boxes to find your coffee maker.

![Dashboard Overview](docs/screenshots/dashboard.png)
*Main dashboard showing all boxes with search and room filtering*

![Box Details & Items](docs/screenshots/box-details.png)
*Manage box contents and assign rooms*

![Public QR Code Preview](docs/screenshots/public-preview.png)
*Share box contents with family members via QR code - no login required*

![Label Printing](docs/screenshots/label-printing.png)
*Print labels with QR codes for easy scanning*

## Core Features

- 📦 **Box Management** - Track boxes with current location and target room assignments
- 📝 **Item Inventory** - Maintain detailed lists of contents for each box
- 🔍 **Global Search** - Quickly find which box contains specific items
- 🔗 **Public QR Sharing** - Share box contents with movers/family via scannable QR codes (no authentication required)
- 🖨️ **Bulk Label Printing** - Print multiple box labels with QR codes at once
- 🌙 **Dark Mode** - Toggle between light/dark themes with cookie persistence
- 🔐 **Nextcloud Authentication** - Secure login via OpenID Connect (OIDC)

## Development

### Prerequisites
- Java 21
- Node.js 20+
- Maven 3.9+

### Backend
```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```
Runs on port 8080 with H2 in-memory database.

### Frontend
```bash
cd frontend
npm install
npm run dev
```
Runs on port 5173 with Vite dev server.

### Configuration Profiles
- `dev` - H2 database, JDBC sessions, OIDC enabled
- `prod` - PostgreSQL, Redis sessions, OIDC enabled
- `test` - H2 in-memory, security disabled

See [application-*.yml](backend/src/main/resources/) for profile configurations.

## Production Deployment

### Prerequisites
- Docker & Docker Compose
- Traefik reverse proxy (must be running externally)
- Nextcloud instance for OIDC authentication

### Setup

1. **Configure environment variables**

Create `.env` file:
```bash
# Database
POSTGRES_USER=boxuser
POSTGRES_PASSWORD=secure-password-here
POSTGRES_DB=boxcopilot

# OAuth2/OIDC (Nextcloud)
CLIENT_ID=your-nextcloud-oauth-client-id
CLIENT_SECRET=your-nextcloud-oauth-client-secret
NEXTCLOUD_URL=https://cloud.example.com
NEXTCLOUD_LOGOUT_URL=https://cloud.example.com/index.php/logout

# Traefik Hosts
TRAEFIK_FRONTEND_HOST=boxes.example.com
TRAEFIK_BACKEND_HOST=boxes-api.example.com
```

2. **Start services**
```bash
docker-compose up -d
```

### Nextcloud OIDC Setup

Register OAuth2/OIDC client in Nextcloud:
- **Redirect URI**: `https://boxes-api.example.com/login/oauth2/code/nextcloud`
- **Grant Type**: Authorization Code
- **Scopes**: `openid`, `profile`, `email`

Use the generated `client_id` and `client_secret` in your `.env` file.

### Reverse Proxy (Traefik)

**Important:** BoxCopilot requires Traefik to be running **externally** before starting the application. The services connect to an existing Traefik instance via the `traefik-proxy` Docker network.

#### Complete Traefik Setup Example

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
      - "traefik.http.routers.traefik-secure.tls.certresolver=cloudflare"
      - "traefik.http.routers.traefik-secure.service=api@internal"

networks:
  traefik-proxy:
    external: false
```

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
  cloudflare:
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

#### Network Configuration

BoxCopilot's `docker-compose.yml` connects to the external `traefik-proxy` network:
```yaml
networks:
  traefik-proxy:
    external: true
```

Both frontend and backend services include Traefik labels for:
- HTTP → HTTPS redirect
- TLS certificate provisioning (Let's Encrypt)
- Host-based routing

## Technology Stack

**Backend:** Java 21, Spring Boot 3.5, Spring Security with OAuth2/OIDC, Spring Data JPA, PostgreSQL, Redis, Flyway

**Frontend:** React 18, TypeScript, Vite, Material-UI, Axios, React Router

**Infrastructure:** Docker, Docker Compose, Traefik, PostgreSQL 15, Redis

## License

This project is licensed under the **Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International License (CC BY-NC-SA 4.0)**.

**You are free to:**
- ✅ Share - copy and redistribute the material
- ✅ Adapt - remix, transform, and build upon the material

**Under the following terms:**
- 📝 **Attribution** - You must give appropriate credit
- 🚫 **NonCommercial** - You may not use the material for commercial purposes
- 🔄 **ShareAlike** - If you remix or build upon the material, you must distribute your contributions under the same license

See [LICENSE](LICENSE) for full details or visit https://creativecommons.org/licenses/by-nc-sa/4.0/

For stricter copyleft requirements, consider the **GNU Affero General Public License v3.0 (AGPL-3.0)** which also requires sharing source code for network-deployed services.
