# BoxCopilot documentation

BoxCopilot tracks what is packed in each moving box and where it has to go. These pages cover everything from the first sign-in to running it in production.

## Using BoxCopilot

| Page | Content |
|---|---|
| [Features](features.md) | What the application does, grouped by area |
| [User guide](user-guide.md) | A walk through the screens with screenshots: boxes, items, labels, public links, administration |
| [Claude and MCP](claude-mcp.md) | Let Claude search your inventory through the optional read-only MCP server |

## Running BoxCopilot

| Page | Content |
|---|---|
| [Deployment](deployment.md) | Production setup with Docker Compose, Nextcloud login and Traefik |
| [Configuration](configuration.md) | Profiles and environment variables |
| [Security](security.md) | Login throttling, proxy trust, sessions, headers, containers, uploads |
| [Upgrading](upgrading.md) | Upgrade notes per release |
| [Backup and restore](ops/backup-restore.md) | Back up and restore the database and the images |
| [Traefik](ops/traefik.md) | Complete Traefik setup and network configuration |
| [MCP operations](ops/mcp-server.md) | Kill-switch, audit log, residual risks |

## Developing BoxCopilot

| Page | Content |
|---|---|
| [Development](development.md) | Local setup, project layout, technology stack |
| [Testing](testing.md) | Backend and frontend tests, browser end-to-end test |
| [API reference](api.md) | All HTTP endpoints |
| [Release notes](releases/) | Changes and upgrade steps per release |
