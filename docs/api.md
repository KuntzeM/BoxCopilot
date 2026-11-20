# API reference

All endpoints are relative to `APP_URL`. Authenticated endpoints use the session cookie and need the CSRF token (`GET /api/v1/csrf`, header `X-XSRF-TOKEN`) on state-changing requests.

## Authentication

| Endpoint | Description |
|---|---|
| `POST /api/auth/login` | Form login with `username` and `password`, form-encoded, with CSRF token. Answers 429 with `Retry-After` when throttled |
| `GET /oauth2/authorization/nextcloud` | Starts the OIDC login |
| `POST /api/v1/auth/magic-login` | Redeems a magic login link (`{"token": "..."}`). Answers 429 with `Retry-After` when throttled |
| `POST /api/auth/logout` | Logs out the current user. Answers 200 with `{"redirectUrl": ...}`: the identity provider logout URL for OIDC users, `APP_URL` otherwise. The SPA navigates there |
| `GET /api/v1/me` | Current user |
| `GET /api/v1/csrf` | Fetches the CSRF token |

## Health

No authentication.

| Endpoint | Description |
|---|---|
| `GET /actuator/health` | `{"status":"UP"}` or `{"status":"DOWN"}` (503), no details. Used by the compose healthcheck |

## User management (ADMIN only)

| Endpoint | Description |
|---|---|
| `GET /api/v1/admin/users` | List all users |
| `GET /api/v1/admin/users/{id}` | Get one user |
| `POST /api/v1/admin/users` | Create a user |
| `PUT /api/v1/admin/users/{id}` | Update a user |
| `PUT /api/v1/admin/users/{id}/password` | Set a password. Ends that user's sessions, invalidates their magic links, revokes their MCP access |
| `DELETE /api/v1/admin/users/{id}` | Delete a user |
| `POST /api/v1/admin/users/{id}/magic-link` | Generate a magic login link |
| `DELETE /api/v1/admin/users/{id}/magic-link` | Invalidate the user's magic links |

## Boxes (authenticated)

| Endpoint | Description |
|---|---|
| `GET /api/v1/boxes` | List all boxes |
| `GET /api/v1/boxes/{uuid}` | Get one box |
| `POST /api/v1/boxes` | Create a box |
| `PUT /api/v1/boxes/{id}` | Update a box |
| `POST /api/v1/boxes/{id}/rotate-share` | Regenerate the public link and image tokens. Old links and printed QR labels stop working |
| `DELETE /api/v1/boxes/{id}` | Delete a box, including its image files |

## Items (authenticated)

| Endpoint | Description |
|---|---|
| `GET /api/v1/items/box/{boxUuid}` | List the items of a box |
| `GET /api/v1/items/search` | Search items |
| `POST /api/v1/items` | Create an item |
| `PUT /api/v1/items/{id}` | Update an item |
| `DELETE /api/v1/items/{id}` | Delete an item |
| `PUT /api/v1/items/{id}/move` | Move an item to another box |
| `PUT /api/v1/items/move-bulk` | Move several items to another box |
| `POST /api/v1/items/{id}/image` | Upload an item image (multipart field `file`, at most 25 megapixels). 400 if the file is not a valid image, 503 with `Retry-After` when busy |
| `DELETE /api/v1/items/{id}/image` | Delete an item image |

## Public access (no authentication)

| Endpoint | Description |
|---|---|
| `GET /api/v1/public/{uuid}` | Box preview by UUID |
| `GET /api/v1/public/items/{token}/image` | Item thumbnail |
| `GET /api/v1/public/items/{token}/image/large` | Large item image |

## Claude and MCP

Only available when `APP_MCP_ENABLED=true`. See [Claude and MCP](claude-mcp.md).

| Endpoint | Description |
|---|---|
| `POST /api/v1/me/mcp-client` | Get, and create if missing, the caller's MCP client ID |
| `POST /api/v1/me/mcp-client/regenerate` | Replace the client ID, deleting the old one and its connections |
| `DELETE /api/v1/me/mcp-connections` | Revoke all of the caller's MCP connections |
| `POST /mcp` | MCP endpoint, OAuth 2.1 bearer token required. See [MCP operations](ops/mcp-server.md) |
