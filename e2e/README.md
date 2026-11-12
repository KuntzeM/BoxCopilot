# BoxPilot browser smoke test

Runs a real headless Chromium journey (login, boxes, items, photo upload and rejection, item form, public link and error page,
share-link rotation, security headers, admin policy, password reset ending sessions, no account lock, session expiry, logout,
OIDC, mobile, login and magic-link throttle, CSP console check) against an already running stack.

    cd e2e && npm install && npx playwright install chromium
    E2E_USERNAME=<admin> E2E_PASSWORD=<pw> npm test      # exits non-zero on any failed step

Optional env: E2E_FRONTEND_URL (default http://localhost:5173), E2E_API_URL (default http://localhost:8080),
E2E_OUT (screenshot/results dir, default a fresh temp dir). The mock OIDC provider must be reachable at oidc.localhost:9090
(docker-compose.e2e.yml). The script creates one box and one user and deletes both at the end; it expects the E2E stack, never production.

Throttle steps: the journey runs from the default client address; the throttle steps simulate separate clients with
`X-Forwarded-For` headers (random addresses per run, so the suite can run repeatedly without restarting the backend). This needs
the backend to trust the Docker gateway as proxy (default `TRUSTED_PROXY_PATTERN`) and the E2E overlay's small limits
(`APP_LOGIN_THROTTLE_IP_LIMIT=8`, `APP_LOGIN_THROTTLE_IP_USER_LIMIT=5`). Container checks (non-root uid, logs) are not part of this script.
