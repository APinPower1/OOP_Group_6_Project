# Local Development Setup

This file uses the shared baseline in `docs/BUILD_BASELINE.md`: Java 21, Spring Boot 3.5.16, Gradle Wrapper 8.14.3 and MySQL 8.4 LTS. Do not change versions independently.

## Requirements

- JDK 21.
- Gradle Wrapper 8.14.3 committed to the repository; do not require a locally installed Gradle after wrapper generation.
- MySQL 8.4 LTS server using InnoDB.
- IntelliJ IDEA, VS Code or another Java IDE.
- No Node.js/React setup; the UI is Swing.

## Setup sequence

1. Clone the repository.
2. Set backend environment variables `DB_USERNAME` and `DB_PASSWORD` to your local MySQL account, plus `DB_URL` if different. Do not commit these values.
3. Create a local MySQL database (example name `student_portal`).
4. Run `database/create_database.sql` once to create the empty `student_portal` database.
5. Start Spring Boot. Flyway applies `backend/src/main/resources/db/migration/V1__initial_schema.sql` automatically.
6. Do not create demo accounts except through the guarded first-admin bootstrap documented below. Demo passwords must be BCrypt-hashed; never store plaintext passwords in SQL or commit credentials.
7. Verify `GET http://localhost:8080/api/health` returns healthy status. Protected API routes require a valid JWT and enforce role/ownership authorization.
8. Start the Swing client using `./gradlew :client:run`.
9. Configure API base URL for the client, normally `http://localhost:8080/api` during local development.
10. Log in with the local first-admin account created by the guarded bootstrap below. Never use real college credentials.

## Environment variables (backend)

- `DB_URL`, example `jdbc:mysql://localhost:3306/student_portal?serverTimezone=Asia/Kolkata`
- `DB_USERNAME` (existing local MySQL account with privileges on `student_portal`)
- `DB_PASSWORD` (local secret; never commit)
- `JWT_SECRET` (random, sufficiently long, local only)
- `JWT_TTL_SECONDS`, suggested `1800`
- `SERVER_PORT`, default `8080`

## Bootstrap the first local administrator

The account bootstrap is a one-time local-development operation. It requires the explicit
`--bootstrap-admin` application argument, validates that the configured JDBC URL targets exactly
the `student_portal` schema on `localhost` or `127.0.0.1`, acquires a MySQL advisory lock, and
refuses to run when any admin account already exists. The password is read only from
`ADMIN_BOOTSTRAP_PASSWORD` and is stored as a BCrypt hash. The helper never prints the password.

Set the DB/JWT environment variables above plus:

- `ADMIN_BOOTSTRAP_EMAIL` (required)
- `ADMIN_BOOTSTRAP_PASSWORD` (required; 8–72 UTF-8 bytes)
- `ADMIN_BOOTSTRAP_NAME` (optional; defaults to `Portal Administrator`)
- `ADMIN_BOOTSTRAP_DEPARTMENT` (optional; defaults to `Administration`)

PowerShell example; enter secrets only at the local prompts and do not commit or paste them into
source files:

```powershell
$env:DB_USERNAME = Read-Host "Local MySQL username"
$secureDbPassword = Read-Host "Local MySQL password" -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new("", $secureDbPassword).Password
$secureJwtSecret = Read-Host "Random JWT secret (at least 32 bytes)" -AsSecureString
$env:JWT_SECRET = [System.Net.NetworkCredential]::new("", $secureJwtSecret).Password
$env:ADMIN_BOOTSTRAP_EMAIL = Read-Host "Admin login email"
$secureAdminPassword = Read-Host "Admin password (8-72 UTF-8 bytes)" -AsSecureString
$env:ADMIN_BOOTSTRAP_PASSWORD = [System.Net.NetworkCredential]::new("", $secureAdminPassword).Password
.\gradlew.bat :backend:bootRun --args="--bootstrap-admin"
```

After startup confirms that the account was created, stop the backend, clear the bootstrap
password variable, and start the backend normally with `.\gradlew.bat :backend:bootRun`. A second
bootstrap attempt is refused once any administrator exists.

## Local timezone

Set backend and MySQL session/server timezone consistently with event-time handling. The recommended local convention is `Asia/Kolkata` and API strings without offsets (`yyyy-MM-dd'T'HH:mm:ss`). If the team later chooses UTC/offset timestamps, update the shared API/schema contract first.

## Troubleshooting

- `401`: missing/expired/invalid token; log in again and ensure the API client attaches `Authorization: Bearer ...`.
- `403`: authenticated role/ownership is not allowed; check backend rules, not just Swing navigation.
- MySQL connection error: confirm server/database/username/password, DB URL, and that credentials remain backend-only.
- `EVENT_FULL`: query the count of `CONFIRMED` registrations; do not manually adjust a stored event count.
- Schema drift: use Flyway to apply committed migrations. Reset only a disposable local database; never drop shared/demo data without warning.

## Database verification

After starting the backend, verify that Flyway applied the expected migration:

```sql
USE student_portal;

SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

## Safe local database reset

**Warning:** Resetting a database permanently deletes its data. Only reset a disposable local database that you own. Never reset a shared, production, or team demo database.

Before resetting:
1. Confirm the exact database name and ensure it contains no data you need.
2. Stop applications and tests connected to that database.
3. Back up any data you need to retain.
4. Recreate the empty database using the approved local setup procedure.
5. Restart the backend so Flyway can apply the committed migrations.

Do not delete Flyway migration files or manually modify `flyway_schema_history` to resolve schema drift. Correct the underlying issue through the team's approved migration process.
