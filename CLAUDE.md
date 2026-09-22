## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).

## Project-wide conventions

BoxCopilot has independent `backend/` and `frontend/` modules. Run Maven commands from `backend/` and npm commands from `frontend/`; there is no root build manifest.

- Do not commit credentials, runtime data, logs, `target/`, or `node_modules/`.
- Treat authentication, authorization, sessions, public sharing tokens, image access, proxy headers, and MCP as security-sensitive.
- Keep backend DTOs and frontend types/API clients synchronized when changing an endpoint.
- Preserve the existing controller -> service -> repository layering and existing transaction boundaries.
- For database changes, add a new Flyway migration under `backend/src/main/resources/db/migration/`; never edit an applied migration.
- Use the nearest existing test as the pattern for new coverage. Run the narrowest relevant check first, then the module suite.
- After changing source code, run `graphify update .`; do not hand-edit generated files under `graphify-out/`.

Module-specific guidance is loaded from `backend/CLAUDE.md`, `backend/src/main/java/com/boxcopilot/backend/*/CLAUDE.md`, and `frontend/CLAUDE.md`.
