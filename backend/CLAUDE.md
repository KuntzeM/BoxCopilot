# Backend Instructions

BoxCopilot backend is Java 21 with Spring Boot 4.1 and Maven. The application entry point is `src/main/java/com/boxcopilot/backend/BoxCopilotApplication.java`.

## Structure

- `config/`: security, startup guards, profile wiring, sessions, and infrastructure configuration.
- `controller/`: HTTP API boundary.
- `service/`: business rules and transactions.
- `repository/`: Spring Data persistence and Testcontainers support.
- `domain/`: JPA entities and enums.
- `dto/` and `mapper/`: API contracts and entity conversion.
- `security/`: login throttling (`LoginThrottle`, `LoginThrottleFilter`) and the shared `FixedWindowRateLimiter`, in-process (one replica, reset on restart). `mcp/` may depend on it; nothing outside `mcp/` may depend on `mcp/`.
- `mcp/`: optional MCP transport, OAuth2, authorization, tools, consent, and abuse controls.
- `exception/` and `event/`: shared error handling and application events.

## Profiles

- `dev`: H2 file database, JDBC sessions, and safe local OIDC defaults.
- `prod`: PostgreSQL, Redis sessions, Flyway, secure cookies, and startup guards.
- `test`: isolated test configuration.
- `mcp-test`: layered test profile for real MCP chains; it uses PostgreSQL Testcontainers.

MCP is off unless `app.mcp.enabled` / `APP_MCP_ENABLED` is true. Production must have an `ADMIN_PASSWORD` of at least 12 characters that is not `admin`, and a Redis password; `StartupGuard` lists all missing required variables in one message.

## Commands

Run from `backend/`:

```powershell
mvn test
mvn -Dtest=SomeTest test
mvn spring-boot:run
```

MCP integration tests need Docker. Do not run the generated JAR directly for local development; use Maven so profiles and configuration are applied.

Keep API behavior covered by controller/service tests, keep security changes covered by regression tests, and add forward Flyway migrations for production schema changes.
