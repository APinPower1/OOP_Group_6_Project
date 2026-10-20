# Authentication, JWT and Authorization

## Required flow

1. Swing posts email/password to `POST /api/auth/login`.
2. Backend loads the enabled user by normalized email and verifies the submitted password against a BCrypt hash.
3. On success, backend signs a short-lived JWT. Suggested access-token lifetime: 30 minutes for demo/MVP.
4. Swing keeps the token only in an in-memory `SessionContext` and attaches it as `Authorization: Bearer <token>` for protected requests.
5. Backend validates signature and expiration and establishes the current principal/authorities.
6. Backend checks both role and record ownership for each operation.
7. Logout clears token and user state from Swing, returns to login and clears any cached protected data.

## JWT claims

Use `sub` for stable user ID or normalized email (pick one consistently; preferred `sub` = numeric user ID as a string), `role`, `iat`, `exp`, and a unique `jti` if supported by the selected library. Do not store passwords or personal profile fields beyond what is required.

If the role may change while a token remains valid, either load current enabled status/role from the database during authentication or use a short expiry and require re-login after role changes. For this student project, backend should verify the current user is still enabled and use current role from the database for authorization.

## Secret handling

- JWT signing secret exists only in backend environment/local ignored config.
- For HMAC, use a randomly generated secret of adequate cryptographic length; never use a short human password.
- Do not commit `.env`, DB passwords, signing secrets, demo production credentials or tokens.
- Do not hard-code MySQL credentials in source code.
- Use placeholder local demo data only.

## Role/ownership rule

Backend authorization is authoritative. Swing may hide buttons by role for convenience, but a user calling an endpoint manually must still be denied by Spring Security/service checks.

Examples:
- Student cannot create or approve an event.
- Organizer cannot approve/reject events.
- Organizer can edit only their own `PENDING_APPROVAL` or `REJECTED` proposal.
- Organizer can cancel only their own event; Admin may cancel as moderator.
- Faculty/Admin may approve or reject pending events.
- Only Admin manages user roles and clubs.
- Student can cancel only their own registration and read only their own full registration history/notifications.
- Organizer can view rosters/feedback only for events they created.

## HTTP behavior

- No/malformed/expired/invalid token: `401` with stable error code `AUTH_REQUIRED` or `TOKEN_INVALID`.
- Token valid but role/owner check fails: `403` with `FORBIDDEN`.
- Login failure must not reveal whether email or password was incorrect; return one generic credential error.
- Disable accounts immediately for new requests. Existing Swing session should be rejected on next API call.

## Desktop client caveats

A Swing program is a distributed client even if initially run on the same computer. It cannot safely hold backend secrets. The database is not accessed from the client. Do not persist JWT to disk in MVP. Use TLS outside localhost development.
