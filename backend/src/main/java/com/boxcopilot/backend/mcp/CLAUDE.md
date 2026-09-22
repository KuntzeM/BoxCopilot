# MCP Backend Instructions

MCP is an optional, read-only integration exposed through Spring AI and protected by its own OAuth2 authorization/resource-server chains.

- Keep MCP disabled by default and preserve the explicit `app.mcp.enabled=true` gate.
- Treat client binding, redirect URI validation, token subject mapping, user MCP permission, revocation, rate limits, and connection age as security boundaries.
- Do not weaken scopes or allow arbitrary redirect URIs, clients, users, or image access.
- Preserve the separation between authorization-server, MCP, and application SecurityFilterChains.
- Use `McpIntegrationTestBase` for real-chain tests; it intentionally uses `dev` + `mcp-test` and PostgreSQL Testcontainers.
- Keep the MCP API read-only and avoid leaking credentials, raw tokens, or private image storage paths in tool results or logs.

Run focused MCP/config tests first, then the full backend suite with Docker available.
