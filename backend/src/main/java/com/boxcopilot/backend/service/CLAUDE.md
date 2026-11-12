# Backend Service Instructions

Services own domain behavior and transaction boundaries. Keep controllers and repositories simple by putting multi-step rules here.

- Preserve `@Transactional` boundaries and think about lazy associations because Open EntityManager in View is disabled.
- Reload security-sensitive state when the existing service does so; do not rely on a stale persistence-context entity for authorization decisions.
- Keep box-number allocation/release, image lifecycle, magic-link redemption, password-reset session invalidation (`sessions_valid_after`), and MCP access revocation atomic. There is no account lockout; failed logins are throttled per IP in `security/`.
- Prefer existing repository methods and domain invariants over duplicating rules in callers.
- Log operational events without passwords, tokens, OAuth credentials, or unnecessary personal data.
- Add a focused service test for each new branch, especially disabled users and authorization failures.

Run the focused service test class first, then the backend suite from `backend/`.
