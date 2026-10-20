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
