# Team Ownership and File Boundaries

Each member owns the module files listed here. The owner reviews/merges changes to those files. Shared contracts are not edited independently by feature agents.

| Member | Primary ownership | Allowed dependencies / coordination |
|---|---|---|
| 1 | Architecture, Gradle scaffold/version pins, package root, common API/error contracts, final merge coordination | Reviews all shared contract changes; does not take over unfinished feature modules by default. |
| 2 | Authentication, JWT/security integration, users/role profiles and admin account management | Must align with `AUTH_AND_SECURITY.md`, `users` schema and role enum. |
| 3 | Event domain/DAO/service/controller, venue clash validation, event approval and cancellation semantics | Uses registration count query contract; coordinates event status with Member 4 and Faculty/Admin permissions. |
| 4 | Registration/roster DAO/service/controller, tickets, transactional locking, registration cancellation/reactivation | Owns registration transaction; schema migration changes only requested through Member 7. |
| 5 | Club and club-membership module | Uses the agreed `clubs` / `club_members` schema. |
| 6 | Announcement, notification and feedback modules | Coordinates notification creation with announcement transaction; uses agreed post-event feedback rules. |
| 7 | SQL migrations/schema, database bootstrap/demo data, JDBC conventions, schema integration verification | Does not implement all feature DAOs; feature owners own their DAOs. Reviews every migration before merge. |
| 8 | Swing Views, client controllers, API client, session context and role-based navigation | Uses only shared DTO/API client contract. Never connects to MySQL. |
| 9 | Unit/integration/end-to-end test plan, regression suite, issue list and final smoke-test evidence | Can add tests across modules; does not independently redesign APIs/schema. |

## Ownership rules

- Shared DTOs and error codes: Member 1 maintains contract; module owner implements its module DTOs consistently.
- SQL migration files: Member 7 creates/reviews. Feature owners send a request and cannot edit the migration silently.
- Gradle Wrapper/build files: Member 1 only.
- Swing API client and shared DTOs: Member 8 creates against `API_CONTRACT.md`; Member 1 approves contract changes.
- Tests: Member 9 owns cross-module acceptance tests; each feature owner owns unit tests for the feature.
- Nobody commits generated secrets, local DB dumps with credentials, or private token files.
