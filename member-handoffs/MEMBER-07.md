# Member 07 — Database Schema, Migrations and JDBC Integration

## Mission
Own relational schema correctness and the shared database contract. Feature owners write their own DAOs; you own migrations, schema setup/verification and JDBC conventions.

## Own
- `backend/src/main/resources/db/migration/**` versioned Flyway migration files
- `database/create_database.sql` and `database/ERD.md`
- Backend local DB configuration example and seed-data procedure (no real credentials)
- Schema/integration checks

## Requirements
- Use MySQL/InnoDB, foreign keys, indexes, unique constraints and CHECK constraints shown in `backend/src/main/resources/db/migration/V1__initial_schema.sql`.
- Profile rows must match `users.role`; service-level constraint cannot be fully enforced by basic FK, so document/test it with Member 2.
- Registration uniqueness/capacity support is critical.
- Do not add `registered_count`; confirmed registration count is derived.
- Maintain one migration history. Never let another member edit a migration silently.
- All DAO SQL uses `JdbcTemplate`/parameters and explicit column mappings; no JPA/Hibernate/Spring Data.
- No shared static live JDBC `Connection`; Spring manages DataSource and transactions.

## Deliverables
- Review schema against the UML corrections and contract.
- Create the empty DB using `database/create_database.sql`; Flyway applies the versioned migration cleanly at backend startup.
- Demo seed plan creates users with BCrypt-hashed demo passwords through a helper or controlled seed tool.
- Verify foreign keys, duplicate guards, check constraints and indexes.
- Document schema reset only for a disposable local database.

## Tests
- Fresh database setup.
- Attempt duplicate email, duplicate registration, duplicate club membership/feedback.
- Invalid rating/capacity/status rejected.
- Foreign key integrity and role-profile mismatch validation.

---

## Status (Member 07)

This section records actual progress against the requirements above. It is descriptive only; it
does not change the mission or requirements.

### Completed and committed

- **`eb3980d` — "test: guard registration database URL".** Adds the test-database URL safety
  guard (`RegistrationTestDatabase.validateUrl`) and the database-free URL test suite
  (`RegistrationTestDatabaseUrlTest`, 17 tests). The guard requires the exact
  `student_portal_test` schema and refuses `student_portal` or any other schema before the
  availability check and before Spring builds the test DataSource.
- **`e416a92` — "feat: add demo seeder database target validator".** Adds
  `edu.mec.studentportal.seed.DatabaseTargetValidator` and its database-free test
  (`DatabaseTargetValidatorTest`, 17 tests). This validator accepts only a MySQL JDBC URL
  targeting the exact application schema `student_portal` and rejects `student_portal_test`
  and every other schema; it is the inverse guard of the registration one.

### Verified tests (previously run, results as reported by the author)

- `RegistrationTestDatabaseUrlTest` — 17 tests, 0 failures/errors (no MySQL required).
- `DatabaseTargetValidatorTest` — 17 tests, 0 failures/errors (no MySQL required).
- Registration integration/concurrency tests run only when a correctly configured
  `student_portal_test` database is reachable (otherwise they skip).

### Existing documentation

- `docs/DATA_MODEL.md` and `database/ERD.md` describe the 12-table schema and relationships.
- `docs/LOCAL_DEVELOPMENT.md` documents application DB setup, Flyway auto-apply, a
  `flyway_schema_history` verification query and a disposable-database reset procedure.
- `docs/TEST_DATABASE_SETUP.md` documents the test schema, restricted account, `TEST_DB_*`
  variables, guard behavior, fail-vs-skip semantics and the collation caveat.

### Current uncommitted documentation changes (not yet committed)

- `docs/LOCAL_DEVELOPMENT.md` — uncommitted additions for Flyway verification and safe local
  reset (preserved as-is).
- `docs/TEST_DATABASE_SETUP.md` — new file, currently untracked.
- This `MEMBER-07.md` status section and the small `docs/DATA_MODEL.md` additions are part of
  the same uncommitted documentation batch.

### Pending implementation

- **Demo-account seeder** (`DemoAccountSeeder`, configuration properties, double opt-in,
  idempotent rerun, safe-target validation) — **not implemented**; only the target validator
  exists.
- **Account provisioning** — **not implemented on any branch** (no `createAccount` /
  `AccountProvisioning`); verified by Git inspection.
- **BCrypt-encoded user creation** — the `PasswordEncoder` bean exists only on
  `origin/feature/member-2-auth`, not on this branch and not on `origin/main`; no password
  encoding is implemented anywhere.
- **Transactional user + role-profile creation** — not implemented.
- **Automated schema-constraint regression tests** (duplicate email/roll number, duplicate
  club membership/feedback, invalid rating/capacity/status, FK/RESTRICT integrity, index and
  CHECK catalog checks) — not implemented.
- **Role/profile consistency enforcement test** — service-level; blocked on Member 02.
- **Constraint-name inventory in `docs/DATA_MODEL.md`** — deferred to a separately reviewed
  follow-up.

### Blocked / coordination required

- **Member 02 account creation.** The seeder depends on an agreed, transactional
  account-provisioning API that hashes passwords, creates the `users` row and the matching
  role-profile row. No such API exists yet. Direct JDBC inserts in the seeder are **not**
  authorized by the schema-shape approval.
- **Shared test-support integration.** The `testsupport` package
  (`DatabaseIntegrationTestSupport`, `TestDatabaseAvailability`, `TestAuthSecurityConfig`) is
  merged on `origin/main` at `4c373ac` but is **not present on this branch**. Its
  `TestDatabaseAvailability` defaults the username to `student_portal_app` and has **no URL
  guard**, which differs from this branch's registration guard; reconciliation is outstanding.
- **Schema regression tests** depend on integrating the shared support above.

### Next steps

1. Obtain the account-provisioning API contract from Member 02 and its merge timeline.
2. Integrate `origin/main` (`4c373ac`) so the shared `testsupport` is available on this branch.
3. Implement the seeder against the approved provisioning API plus `DatabaseTargetValidator`.
4. Implement the schema-constraint regression tests on the shared support.
5. Commit the documentation batch after review (no commit until authorized).

### Useful commands

```bash
# Database-free URL validation suites (no MySQL, no credentials)
./gradlew :backend:test --tests "edu.mec.studentportal.registration.RegistrationTestDatabaseUrlTest"
./gradlew :backend:test --tests "edu.mec.studentportal.seed.DatabaseTargetValidatorTest"

# Full backend suite (DB-backed tests require a reachable student_portal_test database)
TEST_DB_PASSWORD='<local>' ./gradlew :backend:test
```
