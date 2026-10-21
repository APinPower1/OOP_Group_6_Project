# Test Database Setup (Member 07)

This document describes the MySQL integration-test database used by the backend test suite on
this branch: how to create it, how tests find it, and the safety rules that protect real data.

Source of truth: `backend/src/test/java/edu/mec/studentportal/registration/RegistrationTestDatabase.java`
and `backend/src/test/java/edu/mec/studentportal/registration/RegistrationIntegrationTestSupport.java`.

## 1. Purpose and scope

Registration integration and concurrency tests run against a **real MySQL** database, not an
embedded substitute. Concurrency semantics (`SELECT ... FOR UPDATE` row locking), unique-index
races, and CHECK/FK constraint behaviour cannot be faked reliably with an in-memory database.

These tests share a dedicated schema, `student_portal_test`, which is **separate from the
application database** `student_portal` described in `README.md` and `docs/LOCAL_DEVELOPMENT.md`.
Integration-test cleanup deletes rows in bulk (see §7), so the test schema must never be a
database you care about.

Schema migration for the test schema is applied automatically: the test Spring context enables
Flyway (`RegistrationIntegrationTestSupport` sets `spring.flyway.enabled=true`), which executes
`backend/src/main/resources/db/migration/V1__initial_schema.sql` at context startup.

## 2. Test database name and creation

Expected schema name: **`student_portal_test`** (enforced, see §7).

Create it **empty** once, the same way as the application database. There is no committed
script for the test schema; pattern it on `database/create_database.sql`:

```sql
CREATE DATABASE IF NOT EXISTS student_portal_test
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

Do **not** copy data from `student_portal` into the test schema. Flyway creates all tables
when the backend tests first connect.

## 3. Test database account (least privilege)

Use a dedicated MySQL account whose privileges are scoped to the test schema only — for
example `student_portal_test_app`. Grant pattern (choose your own local password; never
commit it):

```sql
CREATE USER 'student_portal_test_app'@'localhost' IDENTIFIED BY '<choose-a-local-password>';
GRANT ALL PRIVILEGES ON student_portal_test.* TO 'student_portal_test_app'@'localhost';
```

Because the account cannot read or write `student_portal`, even a mispointed tool cannot
touch application data with it. This is the same least-privilege principle the README
describes for the application account, applied to the test side.

## 4. Environment variables

The registration test configuration (`RegistrationTestDatabase`) reads three environment
variables. These are **test-only**; they do not affect the running application, which uses
`DB_URL` / `DB_USERNAME` / `DB_PASSWORD` (see `backend/.env.example`).

| Variable | Purpose | Default in code |
|---|---|---|
| `TEST_DB_URL` | JDBC URL of the test schema | `jdbc:mysql://localhost:3306/student_portal_test?serverTimezone=Asia/Kolkata` |
| `TEST_DB_USERNAME` | MySQL account for tests | `student_portal_test_app` |
| `TEST_DB_PASSWORD` | Password for that account | none — tests skip when unset |

## 5. Defaults confirmed in code

- Default URL: `jdbc:mysql://localhost:3306/student_portal_test?serverTimezone=Asia/Kolkata`
- Default username: `student_portal_test_app`
- Password: no default, environment-only.

If your MySQL host, port, or account differs, override `TEST_DB_URL` / `TEST_DB_USERNAME`
in your environment. The URL override must still target the exact `student_portal_test`
schema (§7).

## 6. Configuring the password safely

- Set `TEST_DB_PASSWORD` in your shell environment for the session only:

  ```bash
  export TEST_DB_PASSWORD='<your-local-password>'   # macOS/Linux
  TEST_DB_PASSWORD='<your-local-password>' ./gradlew :backend:test
  ```

  Windows PowerShell:

  ```powershell
  $env:TEST_DB_PASSWORD = '<your-local-password>'
  .\gradlew.bat :backend:test
  ```

- Never put the password in a committed file, a Gradle file, a command that gets logged into
  shared notes, or a test report. The repository's `.gitignore` already excludes `.env` files;
  the code never prints it (see §7).

## 7. Safety restrictions enforced by `RegistrationTestDatabase`

1. **Exact-schema guard.** `TEST_DB_URL` must target exactly `student_portal_test`. The URL
   is validated (via `java.net.URI`) before any availability check and before Spring builds
   the test DataSource. Rejected: the main `student_portal` schema, any other or nested
   schema, missing schema, missing host, embedded userinfo (`user:pass@host`), out-of-range
   ports, wrong protocols, and malformed URLs.
2. **Unsafe URL = hard failure.** An unsafe or malformed URL throws `IllegalStateException`
   with a diagnostic naming the required schema and a safe example URL. It is **not** treated
   as "database unavailable" — the build fails loudly instead of silently skipping every
   database test.
3. **No secret leakage.** Diagnostics never echo the raw URL, its query string, or any
   credentials.

These rules exist because integration-test cleanup (in `RegistrationIntegrationTestSupport`)
runs `DELETE` statements against `registrations`, `events`, all four profile tables, and
`users`. Without the guard, a misconfigured `TEST_DB_URL` could point that cleanup at a
valuable database.

## 8. Fail vs skip — verified behaviour

| Situation | Behaviour | Why |
|---|---|---|
| Unsafe or malformed `TEST_DB_URL` (wrong schema, no schema, bad protocol, userinfo, etc.) | **Fail** — `IllegalStateException`, tests error out | Misconfiguration must never silently disable the safety net |
| `TEST_DB_PASSWORD` unset or blank | **Skip** — database-dependent tests are disabled via `@EnabledIf` | A plain `./gradlew build` stays green on machines without MySQL |
| Correctly configured URL but database unreachable | **Skip** — same `@EnabledIf` path | Same reason |

The `@EnabledIf` condition lives on each concrete database test class and delegates to
`RegistrationTestDatabase.isAvailable()`, which validates the URL **first**, then checks the
password and connectivity. Condition annotations are not inherited, so every new DB-backed
test class must declare the `@EnabledIf` itself.

## 9. Running the URL-validation tests (no MySQL required)

The URL guard has a database-free unit test suite — no Spring context, no connection, no
`TEST_DB_*` variables needed:

```bash
./gradlew :backend:test --tests "edu.mec.studentportal.registration.RegistrationTestDatabaseUrlTest"
```

Windows: `.\gradlew.bat :backend:test --tests "edu.mec.studentportal.registration.RegistrationTestDatabaseUrlTest"`

These 17 tests prove which URLs are accepted and rejected, and that diagnostics redact
credentials and query parameters.

## 10. Running the backend test suite

```bash
TEST_DB_PASSWORD='<your-local-password>' ./gradlew :backend:test
```

- **Without** `TEST_DB_PASSWORD` (or without a reachable test database): the build still
  succeeds; database-backed tests are **skipped**, and only DB-free tests (such as the URL
  suite in §9) execute. Check the test report or console skip messages to confirm what ran.
- **With** the test database configured: registration integration and concurrency tests run
  for real. They **delete all rows** in the tables listed in §7 before each test method —
  only ever point them at `student_portal_test`.

## 11. Troubleshooting

- **Connection failure / tests skip with "cannot reach the required test schema"** — confirm
  MySQL is running, `student_portal_test` exists (§2), and `TEST_DB_URL` host/port are right.
  The message names the required schema but never echoes your full URL or password.
- **"TEST_DB_PASSWORD is not set"** — export the variable in the same shell that runs Gradle
  (IDE run configs often have their own environment settings).
- **`IllegalStateException: Unsafe or misconfigured TEST_DB_URL`** — the URL points at the
  wrong schema (e.g. `student_portal`) or is malformed. Fix `TEST_DB_URL`; do not weaken the
  guard.
- **Access denied for user** — the account exists but its grants are wrong; re-check the
  `GRANT ... ON student_portal_test.*` statement (§3), or set `TEST_DB_USERNAME` to the
  account you actually created.
- **Table-not-found / schema drift errors** — the schema should be created empty and left to
  Flyway; if in doubt, use the disposable-database reset procedure in
  `docs/LOCAL_DEVELOPMENT.md` ("Safe local database reset") against `student_portal_test`
  only.

## 12. Warning — never aim integration tests at a valuable database

Integration-test cleanup is **destructive by design** (full-table `DELETE`s). Never:

- point `TEST_DB_URL` at `student_portal`, a shared demo database, or anything containing
  data you need;
- widen the test account's grants beyond `student_portal_test.*`;
- bypass or disable the URL guard.

The guard exists to make the first mistake fail loudly instead of deleting data. Do not work
around it — fix the URL.

## 13. Shared test-support reconciliation (work in progress)

A shared `testsupport` package exists on `origin/main` at commit **`4c373ac`** (merged via
Member 5's work). The files present on `origin/main` are:

- `backend/src/test/java/edu/mec/studentportal/testsupport/DatabaseIntegrationTestSupport.java`
- `backend/src/test/java/edu/mec/studentportal/testsupport/TestAuthSecurityConfig.java`
- `backend/src/test/java/edu/mec/studentportal/testsupport/TestDatabaseAvailability.java`

`origin/main` also contains module-level test support such as
`backend/src/test/java/edu/mec/studentportal/club/ClubIntegrationTestSupport.java` and
`backend/src/test/java/edu/mec/studentportal/event/EventIntegrationTestSupport.java`.

**Status on this branch (`feature/member-07-database`):** the `testsupport` package is **not
present** yet. Do **not** copy or duplicate those files from another branch — they must be
integrated via the approved branch-merge process. On this branch, the registration package's
own `RegistrationIntegrationTestSupport` / `RegistrationTestDatabase` remain the active test
infrastructure.

**Compatibility / safety concern (not resolved):** on `origin/main`,
`TestDatabaseAvailability` defaults `TEST_DB_USERNAME` to **`student_portal_app`** and has
**no URL-safety guard** equivalent to this branch's `RegistrationTestDatabase.validateUrl`.
By contrast, this branch's registration tests enforce an exact-schema guard for
`student_portal_test`. Any future use of the shared `testsupport` classes must account for
this difference and the risk of misconfiguration; this is outstanding coordination work.

## 13a. Demo-seeder target validator (`DatabaseTargetValidator`)

`edu.mec.studentportal.seed.DatabaseTargetValidator` (committed in **`e416a92`**) is a
separate, database-free guard intended for the planned demo-account seeder.

- **Accepted target:** exactly `student_portal` (the application schema). The example URL is
  `jdbc:mysql://localhost:3306/student_portal?serverTimezone=Asia/Kolkata`.
- **Rejected:** `student_portal_test`, any other or nested schema, embedded userinfo
  (`user:pass@host`), missing host/schema, wrong protocols, invalid ports (out of range),
  and malformed URLs.
- **Safety:** exception messages never echo the raw URL, its query string, or credentials;
  non-identifier schemas are redacted. No database connection or SQL is made.
- **Distinction from the registration guard:** `RegistrationTestDatabase` targets
  **`student_portal_test`** for integration tests; `DatabaseTargetValidator` targets
  **`student_portal`** for demo seeding. They are **inverse** guards and must not be
  conflated.
- **Database-free test command:**
  ```bash
  ./gradlew :backend:test --tests "edu.mec.studentportal.seed.DatabaseTargetValidatorTest"
  ```
  (17 tests; no MySQL required.) This is **pending implementation only** (the validator
  exists; the full demo seeder is not yet implemented).

## 14. Collation caveat (case-insensitive email uniqueness)

`docs/DATA_MODEL.md` states that email uniqueness is case-insensitive. That property comes
from the database collation (`utf8mb4_0900_ai_ci` is set by `database/create_database.sql`
for the **application** database), not from the migration itself — `V1__initial_schema.sql`
only declares `UNIQUE (email)`.

Whether `student_portal_test` actually has that collation depends on **how that schema was
created on your machine** (see the example statement in §2). Verify before relying on
case-insensitive behaviour in tests:

```sql
SELECT DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME
FROM information_schema.SCHEMATA
WHERE SCHEMA_NAME = 'student_portal_test';
```

If your test schema was created with different defaults, either recreate it (disposable
database only, see `docs/LOCAL_DEVELOPMENT.md`) or accept case-sensitive email uniqueness
locally and note the difference.
