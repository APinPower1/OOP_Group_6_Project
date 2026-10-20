# HANDOFF — read before asking an AI to code

## Project understanding

Build a role-based college Student Portal with Event Management System for an OOP course. It is a desktop Java Swing application backed by a Spring Boot API and MySQL database. The project demonstrates inheritance, encapsulation, polymorphism, interfaces, collections, custom exceptions, MVC, DAO separation, JDBC, database constraints, transactions, and JWT-based authentication.

The four roles are `STUDENT`, `ORGANIZER`, `FACULTY`, and `ADMIN`.

## Non-negotiable architecture

1. Swing is the UI/client only.
2. Swing calls backend endpoints using HTTP and JSON.
3. Spring Boot is the only component that connects to MySQL.
4. Use JDBC/Spring JDBC (`JdbcTemplate`) and explicit SQL. **Do not use JPA, Hibernate entities, or Spring Data repositories.**
5. Spring Security verifies JWTs and enforces role permissions on the backend.
6. Keep the architecture layered: Swing View → client controller/state → API client → REST Controller → Service → DAO/JDBC → MySQL.
7. Controllers must not contain SQL or non-trivial business logic. DAOs must not contain UI logic or make authorization decisions.
8. Use PreparedStatement parameters / `JdbcTemplate` parameters for all values. Never concatenate user input into SQL.
9. Seat validation, registration insert/reactivation, and capacity checks must be one database transaction with the event row locked using `SELECT ... FOR UPDATE`.
10. No AI agent may add an alternate endpoint, table, status, role, ID type, JSON property, package root, or dependency version without an agreed contract change.

## MVP scope

- Login and role-based access for all four roles.
- Student, Organizer, Faculty and Admin dashboards.
- Event browse/details, create, edit while awaiting review, approve/reject, cancel, venue-clash validation and registration-capacity tracking.
- Event registration with duplicate protection, capacity protection, ticket number, cancellation and seat release.
- Club listing and student membership.
- Announcements and an in-app notification record for each intended recipient.
- Event feedback with 1–5 rating and optional comment.
- Admin user/role management and basic participation reports.
- MySQL persistence and an integrated Swing-to-API-to-MySQL workflow.

Do not build chat, music interests, materials sharing, timetable/academic modules or other stretch features until the MVP passes acceptance tests.

## Decisions already made in this pack

- A newly created event has status `PENDING_APPROVAL`.
- Only `APPROVED` events are visible to students as registrable events.
- Faculty and Admin can approve/reject. Admin can moderate on behalf of Faculty.
- Venue conflicts are checked using exact venue text after trim/case normalization and overlapping start/end times. Re-check conflicts at approval time.
- `registered_count` is not stored as an independently updated column. Count active `CONFIRMED` registrations so the source of truth is not duplicated.
- An event cannot be edited after approval; if its schedule or venue must change, cancel it and create a new proposal. Capacity can be increased only if it is at least the current confirmed registration count; do not decrease below it.
- Cancelling an event cancels its confirmed registrations in the same transaction and notifies affected students.
- A cancelled registration can be reactivated for the same event if registration remains open and a seat exists; reuse its unique student/event row, update timestamp and generate a fresh ticket number.
- A student may submit one feedback record per event, only after the event's end time, and only if the student has a confirmed registration for it.
- Membership is many-to-many; a unique student/club pair is kept and a left member can rejoin by reactivating the row.
- Logout on the desktop clears the in-memory JWT. No refresh-token or denylist system is in MVP.

## Before implementation

The pack pins a shared baseline in `docs/BUILD_BASELINE.md` and central `build.gradle.kts` files. Member 1 must generate and commit the Gradle Wrapper exactly once; all other members must use it and must not change shared versions.
