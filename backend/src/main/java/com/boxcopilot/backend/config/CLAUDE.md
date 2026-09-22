# Backend Configuration Instructions

This area controls security chains, startup validation, profile-specific wiring, sessions, OAuth2/OIDC, Redis, and application infrastructure.

- Treat every change as security-sensitive and inspect the neighboring config tests before editing.
- Preserve the distinction between `dev`, `prod`, `test`, and `mcp-test` profiles.
- Keep production startup guards intact: empty/default admin credentials and missing required infrastructure configuration must fail fast.
- Keep MCP beans and the `/mcp` endpoint disabled unless `app.mcp.enabled=true`.
- Do not add a new SecurityFilterChain without testing ordering, matcher ownership, and the unaffected chains.
- Do not enable remote OIDC discovery in tests; use the existing test registration pattern.
- Preserve proxy-header trust, secure-cookie, session-store, and CORS behavior. Never broaden trusted proxy settings casually.

Run the narrowest config test first, for example `mvn -Dtest=OAuth2SecurityConfigTest test`, then the backend suite from `backend/`.
