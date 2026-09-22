# Frontend Instructions

BoxCopilot frontend is React 19 + TypeScript + Vite with Material UI. Run commands from `frontend/`.

## Structure

- `pages/`: route-level screens and workflows.
- `components/`: reusable UI.
- `services/`: Axios/API communication.
- `context/` and `hooks/`: shared state and behavior.
- `types/`: API and domain types.
- `i18n/`: German and English translations.
- `styles/`: theme and global styling.

## Rules

- Keep API paths, credentials, error handling, and response shapes compatible with the backend. Vite proxies `/api`, `/oauth2`, and `/login` during development.
- Add or update TypeScript types when an API response changes; do not use broad `any` to bypass mismatches.
- Put reusable API behavior in `services/`, not directly in multiple components.
- Use existing context/hooks for auth and shared state. Avoid duplicating session or user state locally.
- Route public preview flows separately from authenticated dashboard/admin flows.
- Preserve image upload/preview/delete behavior, QR-code links, label PDF generation, and mobile camera behavior when touching item screens.
- Route all user-visible strings through the existing i18n resources.
- Follow the existing Material UI theme and responsive patterns rather than introducing a second styling system.

## Commands

```powershell
npm install
npm test -- --run
npm run build
npm run dev
```

Run the focused Vitest test first, then the full test suite and production build.
