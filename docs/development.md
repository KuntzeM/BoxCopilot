# Development

## Prerequisites

- Java 21
- Node.js 20 or newer (22 works)
- Maven 3.9 or newer

The `dev` profile uses a file-based H2 database and JDBC sessions, so no PostgreSQL or Redis is needed.

## Backend

The backend resolves the OIDC issuer on startup, so `NEXTCLOUD_URL` must point to a reachable OIDC provider. That can be a real Nextcloud or a mock, see [Testing](testing.md).

```bash
cd backend
export SPRING_PROFILES_ACTIVE=dev
export APP_URL=http://localhost:5173   # origin the browser uses (the Vite dev server below)
export CLIENT_ID=your-client-id CLIENT_SECRET=your-client-secret
export NEXTCLOUD_URL=https://cloud.example.com
export NEXTCLOUD_LOGOUT_URL=https://cloud.example.com/index.php/logout
mvn spring-boot:run
```

The backend listens on port 8080 and writes data to `./data/boxcopilot-dev`. Run it with Maven, not as a JAR, so that profiles and configuration are applied.

On first start the bootstrap admin is created from `ADMIN_USERNAME` and `ADMIN_PASSWORD`: username `admin`, password `admin` (dev profile only), role `ADMIN`.

In `prod`, `ADMIN_PASSWORD` has no default. Startup fails if it is empty, `admin` or shorter than 12 characters, or if an existing ADMIN account still has the password `admin`. Set `REDIS_PASSWORD` as well.

## Frontend

```bash
cd frontend
npm install
npm run dev
```

The dev server runs on http://localhost:5173. The SPA uses relative URLs only. Vite proxies `/api`, `/oauth2`, `/login`, `/mcp` and `/.well-known` to the backend on port 8080, so the browser sees one origin, as in production.

Open http://localhost:5173 and sign in with `admin` / `admin`.

## Project layout

BoxCopilot has two independent modules and no root build manifest. Run Maven from `backend/` and npm from `frontend/`.

| Path | Content |
|---|---|
| `backend/` | Java 21, Spring Boot 4.1, Maven |
| `frontend/` | React 19, TypeScript, Vite, Material UI |
| `e2e/` | Playwright browser smoke test against the full Docker bundle |
| `docs/` | Product documentation |
| `Dockerfile` | Builds both modules into one image |
| `docker-compose.yml` | Production stack: app, PostgreSQL, Redis |
| `docker-compose.e2e.yml` | Test overlay with a mock OIDC provider |

The backend follows a controller, service, repository layering: `controller/` is the HTTP boundary, `service/` holds business rules and transactions, `repository/` is Spring Data, `domain/` holds the JPA entities. `dto/` and `mapper/` hold the API contracts. `config/` and `security/` cover security, sessions and startup guards, and `mcp/` is the optional MCP server.

The frontend keeps screens in `pages/`, reusable UI in `components/`, API calls in `services/`, shared state in `context/` and `hooks/`, and German and English strings in `i18n/`. Every user-visible string goes through the i18n resources.

## Conventions

- Keep backend DTOs and frontend types and API clients in sync when an endpoint changes.
- Schema changes need a new Flyway migration. Never edit an applied one.
- The production image copies the SPA bundle into `backend/src/main/resources/static/`. That directory is generated and git-ignored.

## Technology stack

- Backend: Java 21, Spring Boot 4.1, Spring Security with OAuth2/OIDC, Spring Data JPA, PostgreSQL, Redis, Flyway
- Frontend: React 19, TypeScript, Vite, Material UI, Axios, React Router
- Infrastructure: Docker, Docker Compose, Traefik, PostgreSQL 15, Redis
