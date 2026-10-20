# Member 08 — Swing Desktop Client and REST API Client

## Mission
Build a coherent Java Swing app with role-specific dashboards and connect it to the API using one shared HTTP client.

## Own
- `client/**` including API client, session context, navigation, views and client-side DTOs
- UI smoke tests where practical

## Roles/screens
- Login.
- Student dashboard: approved event list/details, register/cancel, club list/join/leave, announcements/notifications, feedback after event.
- Organizer dashboard: own proposals, create/edit pending/rejected proposals, cancel own approved events, roster, post announcement, view own-event feedback.
- Faculty dashboard: pending proposal queue with approve/reject and reason.
- Admin dashboard: users/roles, clubs, announcements, reports and event moderation.

## API client contract
- Use one shared API client with the base URL from config.
- Attach `Authorization: Bearer <token>` to authenticated calls.
- Parse `API_CONTRACT.md` JSON response names/errors.
- Keep JWT in memory only; logout and `401` clear session.
- No database driver/config/connection in client.
- Do not invent API endpoints or fields; UI calls endpoints documented in `API_CONTRACT.md`.

## Swing quality
- UI creation/updates on EDT.
- HTTP on background threads (`SwingWorker` or approved helper).
- Show loading/empty/success/error states; avoid freezing the UI.
- Role-based hiding improves UX but is not the security mechanism.
- Use the shared client DTOs; do not create competing representations for event status/role.

## Tests
- Login/session clearing.
- Client includes bearer token.
- `401` returns to login; `403` shows access denied.
- Each role gets appropriate navigation.
- Registration errors map to useful dialog messages.
