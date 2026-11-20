# Claude and MCP

BoxCopilot can run a read-only [Model Context Protocol](https://modelcontextprotocol.io/) server inside the backend, so Claude can search the inventory on a user's behalf. The feature is off by default (`APP_MCP_ENABLED=false`).

## What Claude can do

Four tools, all read-only:

- `search_items`
- `get_item_image`
- `search_boxes`
- `get_box_contents`

No box UUIDs, image tokens or write access cross this boundary.

## Setting it up

1. An operator sets `APP_MCP_ENABLED=true`. `APP_URL` is the MCP issuer and must be https in production.
2. An admin enables `mcp_allowed` for the user in the Administration page.
3. The user opens Claude connection in the app menu. The dialog shows a personal client ID and the connector URL (`APP_URL` + `/mcp`), both copyable with one click.
4. In Claude, the user adds a custom connector with the connector URL and enters the client ID as the OAuth client ID in the advanced settings.
5. On first use Claude opens a BoxCopilot sign-in and consent screen. After the user confirms, the connection is active.

Each user has their own client ID, bound to their account. Pasting someone else's ID into Claude does not give access to that person's data.

## Managing a connection

The Claude connection dialog has two actions:

- Regenerate creates a new client ID and invalidates the old one together with all its connections.
- Revoke all connections cuts every active connection of that user at once.

Claude then needs to be connected again.

The data Claude queries is sent to the AI provider. The dialog says so as well.

## Operations

The kill-switch, the nightly cleanup job, the audit log, rate limits, residual risks and every MCP environment variable are in the [MCP operations guide](ops/mcp-server.md). The endpoints are listed in the [API reference](api.md#claude-and-mcp).
