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
6. Load only demo/test users through the seed script or documented setup; their passwords must be hashed through the application or a known test helper.
7. Verify `GET http://localhost:8080/api/health` returns healthy status. The bootstrap security config deliberately denies all other API routes until Member 2 completes JWT security.
8. Start the Swing client using `./gradlew :client:run`.
9. Configure API base URL for the client, normally `http://localhost:8080/api` during local development.
10. Log in with documented local demo credentials once Member 2's login module and seed accounts are available. Never use real college credentials.

## Environment variables (backend)

- `DB_URL`, example `jdbc:mysql://localhost:3306/student_portal?serverTimezone=Asia/Kolkata`
- `DB_USERNAME` (existing local MySQL account with privileges on `student_portal`)
- `DB_PASSWORD` (local secret; never commit)
- `JWT_SECRET` (random, sufficiently long, local only)
- `JWT_TTL_SECONDS`, suggested `1800`
- `SERVER_PORT`, default `8080`

## Local timezone

Set backend and MySQL session/server timezone consistently with event-time handling. The recommended local convention is `Asia/Kolkata` and API strings without offsets (`yyyy-MM-dd'T'HH:mm:ss`). If the team later chooses UTC/offset timestamps, update the shared API/schema contract first.

## Troubleshooting

- `401`: missing/expired/invalid token; log in again and ensure the API client attaches `Authorization: Bearer ...`.
- `403`: authenticated role/ownership is not allowed; check backend rules, not just Swing navigation.
- MySQL connection error: confirm server/database/username/password, DB URL, and that credentials remain backend-only.
- `EVENT_FULL`: query the count of `CONFIRMED` registrations; do not manually adjust a stored event count.
- Schema drift: use Flyway to apply committed migrations. Reset only a disposable local database; never drop shared/demo data without warning.
