# BoxPilot browser smoke test

Runs a real headless Chromium journey (login, boxes, items, photo upload, public link, admin, session expiry, logout, OIDC, mobile) against an already running stack.

    cd e2e && npm install && npx playwright install chromium
    E2E_USERNAME=<admin> E2E_PASSWORD=<pw> npm test      # exits non-zero on any failed step

Optional env: E2E_FRONTEND_URL (default http://localhost:5173), E2E_API_URL (default http://localhost:8080),
E2E_OUT (screenshot/results dir, default a fresh temp dir). The mock OIDC provider must be reachable at oidc.localhost:9090
(docker-compose.e2e.yml). The script creates one box and deletes it at the end; it expects the E2E stack, never production.
