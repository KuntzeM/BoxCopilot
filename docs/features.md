# Features

## Boxes

- Create a box with a current room, a target room, a description and handling flags (Fragile, Do not stack).
- Search by item name, filter by target room and, under Filters, by fragile, non-stackable or label-printed boxes. A status filter switches between boxes that are not moved yet, all boxes and moved boxes.
- A progress card on the dashboard shows how many boxes have reached their target room.
- Quick actions per box: mark as moved with one tap (with Undo), edit, copy or open the public link, delete. Expand a box in the list to preview its items.
- Select several boxes to mark them as moved or as label printed in one step (Bulk update), or to print their labels.
- Every box has a public page that works without a login. The link can be regenerated per box. The old link and all printed QR labels of that box stop working.
- Label printing produces an A4 PDF with three labels per page: box number, handling badges, target room, description and a QR code that points to the public page.

## Items

- Create, edit and delete items within a box.
- Move one or many items to another box.
- Add a photo with the in-app camera or from the gallery. Thumbnails and full-size previews are available, and images can be deleted.
- The item dialog has Save and next, which keeps the dialog open for the next item.

## Camera

The in-app camera is built for phones: front and rear camera switch, pinch to zoom, torch when the device supports it, and a capture, retake and accept flow.

## Public preview

- Read-only preview at `/public/{uuid}`, meant for helpers and family.
- Item images come through tokenized endpoints with caching for fast loads.

## Administration and authentication

- The admin panel manages users, roles and passwords and creates magic login links. Setting a user's password ends that user's sessions.
- Two sign-in methods: local username and password, and Nextcloud (OIDC). Failed form and magic-link logins are throttled per client IP, see [Security](security.md).
- Admins can also allow single users to connect Claude, see [Claude and MCP](claude-mcp.md).

## Language and theme

The interface is available in German and English, and in a light and a dark theme. Both choices are remembered in cookies. The layout adapts to phones, tablets and desktops. The design is mobile first: a bottom bar on phones and a rail on desktops give one-tap access to the box list, to creating a box or item and to search, and the administration entries live in the avatar menu.

## Claude and MCP

An optional, read-only [Model Context Protocol](https://modelcontextprotocol.io/) server is built into the backend. It is off by default. See [Claude and MCP](claude-mcp.md).
