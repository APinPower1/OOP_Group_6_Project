# Member 02 — Authentication, JWT and User Management

## Mission
Implement login, backend JWT security, the common `User` abstraction/role profile mapping and Admin account/role management.

## Own these backend areas
- `auth/**`, `security/**`, and `user/**` (coordinate common error classes with Member 1)
- Replace/extend `config/BootstrapSecurityConfig` with stateless JWT security and the correct endpoint permission rules. Do not leave the bootstrap `denyAll` policy in place.
- Auth/user unit tests and security tests

## Contract requirements
- Roles are exactly `STUDENT`, `ORGANIZER`, `FACULTY`, `ADMIN`.
- Login: `POST /api/auth/login`; current principal: `GET /api/auth/me`.
- Passwords use BCrypt hash; failures return generic credential error.
- Disabled users cannot authenticate or keep using protected endpoints.
- JWT is signed/validated on backend only; secret is env/local config only.
- Backend checks current role and enabled state; client-supplied role/creator IDs are not trusted.
- Admin CRUD/manage endpoints match `API_CONTRACT.md` and profile consistency rules in `DATA_MODEL.md`.

## OOP mapping
Implement abstract `User` and concrete role types, or an agreed equivalent matching the class contract. Common fields belong in `User`. Role-specific fields belong in profile objects. Do not attempt a database ORM inheritance strategy; use explicit JDBC queries and role-profile mapping.

## Tests
- Valid/invalid/disabled login.
- Token missing, invalid, expired.
- Each role's protected endpoint permissions.
- Admin can create/disable users and assign role; non-admin cannot.
- Role change and profile update occur transactionally.

## Do not
- Put the JWT secret in the Swing project.
- Store plain-text passwords.
- Let the client pick its own authenticated identity for student actions.
- Change role spelling or profile table schema without approval.
