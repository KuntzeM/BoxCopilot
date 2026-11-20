# Testing

## Unit and integration tests

Run the narrowest check first, then the module suite.

```bash
# backend (from backend/)
mvn test
mvn -Dtest=SomeTest test

# frontend (from frontend/)
npm test -- --run
npm run build
```

The MCP integration tests need Docker because they start PostgreSQL with Testcontainers.

## Browser end-to-end test

`e2e/smoke.mjs` drives a real headless Chromium against the full Docker bundle. The overlay `docker-compose.e2e.yml` runs the app on http://localhost:8080 and a mock OIDC provider that stands in for Nextcloud.

The journey covers login, boxes, items, photo upload and rejection, the item form, the public link and error page, share-link rotation, security headers, admin policy, password reset ending sessions, no account lock, session expiry, logout, OIDC, mobile, login and magic-link throttling, and a CSP console check.

Start the stack with credentials from an env file, never from the command line history:

```bash
docker compose -p boxpilot-e2e --env-file <e2e.env> -f docker-compose.yml -f docker-compose.e2e.yml up -d --build
```

Run the test:

```bash
cd e2e
npm install
npx playwright install chromium
E2E_USERNAME=<admin> E2E_PASSWORD=<password> npm test
```

The script exits non-zero when a step fails. It creates one box and one user and deletes both at the end, so run it against the E2E stack only, never against production.

Stop and remove the stack afterwards:

```bash
docker compose -p boxpilot-e2e --env-file <e2e.env> -f docker-compose.yml -f docker-compose.e2e.yml down -v
```

Optional variables:

| Variable | Default | Purpose |
|---|---|---|
| `E2E_BASE_URL` | `http://localhost:8080` | The single origin that serves the SPA and the API |
| `E2E_OUT` | a fresh temp directory | Where screenshots and results go |

The mock OIDC provider must be reachable at `oidc.localhost:9090`. The throttle steps simulate separate clients with `X-Forwarded-For` headers and random addresses per run, so the suite can run repeatedly without restarting the backend. This needs the default `TRUSTED_PROXY_PATTERN` and the small limits from the E2E overlay (`APP_LOGIN_THROTTLE_IP_LIMIT=8`, `APP_LOGIN_THROTTLE_IP_USER_LIMIT=5`). Container checks such as the non-root uid and the logs are not part of the script.
