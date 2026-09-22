---
name: e2e-testing
description: "Run complete BoxPilot end-to-end tests with Docker and a live browser. Use for E2E testing, Playwright browser tests, full-bundle verification, username/password login, OAuth/OIDC flows, or mocking Nextcloud OAuth."
argument-hint: "Describe the user flow or regression to test"
user-invocable: true
---

# BoxPilot End-to-End Testing

## Purpose

Use this skill to verify the complete deployed bundle, not just isolated frontend or backend tests. A successful run must exercise the browser against the running HTTP services and must include an authenticated flow.

## Non-negotiable rules

- Run the application through Docker Compose. Do not claim E2E coverage from `npm test`, Vitest, JUnit, MockMvc, or direct component tests alone.
- Use a real browser controlled by Playwright (Chromium by default). Do not replace browser navigation with API calls or DOM-only unit tests.
- Test the built frontend and running backend together. Prefer a production-like frontend container and backend container; use a Vite server only when the scenario explicitly targets development proxy behavior.
- Supply the login username and password through environment variables or the test runner's secret store. Never hard-code, print, commit, or put credentials in screenshots, traces, reports, `.env` files, or source code.
- Cover local form login with the actual username/password fields. Verify the post-login authenticated UI and at least one protected API-backed operation.
- Cover Nextcloud OAuth/OIDC using a mock provider when the real Nextcloud tenant is unavailable, unstable, private, or unsuitable for repeatable tests. Do not stub BoxPilot's own `/oauth2`, `/login`, or `/api` endpoints.
- Test the complete bundle before narrowing to a focused regression. A focused test is supplemental, not a substitute for the full smoke path.

## Project entry points

- Frontend commands run from `frontend/`; backend Maven commands run from `backend/`.
- The existing Compose stack provides `postgres`, `redis`, `backend`, and `frontend`. Use a test-only Compose override for browser tooling or a mock OAuth provider; do not weaken the default production configuration.
- The local frontend proxy exposes `/api`, `/oauth2`, and `/login` to the backend. The form login endpoint is `/api/auth/login` and the Nextcloud entry point is `/oauth2/authorization/nextcloud`.
- The dev profile creates the default admin from `ADMIN_USERNAME` and `ADMIN_PASSWORD`. For E2E runs, set both explicitly to non-default test credentials and ensure the database is isolated or reset.

## Required workflow

1. Inspect the current Compose files, frontend scripts, backend profile, and existing test conventions before adding or changing tests.
2. Confirm Docker is available and validate the rendered configuration with `docker compose config`. Fail early if required variables or services are missing.
3. Create or use an E2E-only Compose overlay. It must provide:
   - the complete application bundle and its dependencies;
   - a health/readiness check for the backend and frontend;
   - an isolated database/session state;
   - a mock OIDC provider for Nextcloud tests; and
   - a browser test runner with the required Playwright browser installed.
4. Start the stack with a rebuild, wait for service readiness, and record the base URL used by the browser. Do not use fixed sleeps as the only readiness check.
5. Run a live-browser smoke journey:
   - open the login page over HTTP;
   - log in using `E2E_USERNAME` and `E2E_PASSWORD` (or the repository's documented equivalent);
   - verify the authenticated identity and a protected data request;
   - perform one representative create/edit/read/delete or upload workflow;
   - log out and verify that a protected page redirects or becomes inaccessible.
6. Run the Nextcloud mock journey:
   - navigate through BoxPilot's real Nextcloud authorization endpoint;
   - complete the authorization and login pages served by the mock provider with mock-provider credentials;
   - return through the real callback and verify the BoxPilot authenticated session;
   - assert that the expected identity attributes are mapped to the application user; and
   - verify logout/session cleanup when that behavior is part of the flow.
7. Run the full E2E suite against the built bundle. Preserve Playwright HTML output, screenshots, video, and trace artifacts outside the application source tree.
8. On failure, collect browser console/network errors and container logs, then stop and remove the E2E stack. Do not expose secret-bearing headers, cookies, authorization codes, client secrets, or passwords in the report.
9. Run the narrowest relevant frontend/backend checks after the browser run, then report both results. A green unit suite cannot compensate for a failed live-browser or Docker check.

## Mock Nextcloud/OIDC contract

The mock provider must implement the OIDC behavior that Spring Security actually consumes rather than intercepting the application callback:

- issuer discovery at `${issuer}/.well-known/openid-configuration`;
- authorization endpoint with a real authorization-code redirect;
- token endpoint that validates the code and returns an ID token/access token;
- JWKS endpoint with a stable signing key for the test run; and
- userinfo endpoint or equivalent claims containing a deterministic subject, username, name, and email.

Keep the mock issuer URL reachable from the backend container and the browser. Configure `CLIENT_ID`, `CLIENT_SECRET`, `NEXTCLOUD_URL`, and `NEXTCLOUD_LOGOUT_URL` through the E2E environment/Compose overlay. Use a dedicated mock user and assert the mapped username; do not use the production Nextcloud URL in repeatable CI tests.

## Completion gate

The task is complete only when all of the following are true:

- Docker Compose configuration validates and all required containers become ready.
- A real browser completes username/password login with environment-supplied credentials.
- At least one protected, API-backed application workflow succeeds.
- The browser completes the Nextcloud OAuth/OIDC flow through the mock provider and the real BoxPilot callback.
- The frontend production build and backend package/startup path used by the E2E stack succeed.
- Failure artifacts are available without secrets, and the E2E stack is cleaned up.

If the repository does not yet contain an E2E runner or mock provider, add those as test-only assets and document the exact command in the test package. Do not silently downgrade the work to unit tests or mocked browser requests.