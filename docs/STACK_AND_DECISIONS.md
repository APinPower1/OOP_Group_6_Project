# Stack and Shared Decisions

## Team stack

| Layer | Choice | Rules |
|---|---|---|
| Language | Java | Use the same JDK/toolchain configured in the shared Gradle build. Java 21 is the recommended baseline. |
| Backend | Spring Boot REST API | All domain rules, authentication and authorization live here. |
| Database access | JDBC through Spring JDBC / `JdbcTemplate` | Use explicit SQL and RowMappers. Do not use JPA/Hibernate/Spring Data. |
| Database | MySQL | Use InnoDB and foreign-key constraints. The backend owns the connection pool and credentials. |
| Build | Gradle Wrapper | Only Member 1 updates wrapper/plugin/dependency versions; other members run `./gradlew` or `gradlew.bat`. |
| Desktop UI | Java Swing | Client only. No direct JDBC connection to MySQL. |
| Authentication | JWT + Spring Security | Backend signs and validates tokens; frontend sends `Authorization: Bearer <token>`. |
| HTTP client | Java `java.net.http.HttpClient` | Member 8 implements the one shared API client using the JDK HTTP client; no alternate client libraries without approval. |
| Tests | JUnit + Spring Boot test support | Use unit tests for business rules and integration tests for API/database behavior. |

## Version baseline

The central baseline is Java 21, Spring Boot 3.5.16, Gradle Wrapper 8.14.3, and MySQL 8.4 LTS. See `docs/BUILD_BASELINE.md` and the root/module Gradle build files. Member 1 generates and commits the Wrapper once. Do not let separate AIs invent their own versions. Pin dependency versions through Spring Boot dependency management where possible; do not add versions to individual dependencies without a reason.

## Architecture decisions

1. **Swing is a remote client.** It calls the backend via HTTP/JSON; it never reads or writes MySQL directly.
2. **One repository, one shared Gradle build decision.** Member 1 owns project scaffolding and common build configuration.
3. **MVC and service/DAO layering.** Swing has Views and client Controllers; backend has REST Controllers, Services and DAOs.
4. **JDBC means explicit SQL.** Use `JdbcTemplate`, `RowMapper`, `KeyHolder` where useful and `@Transactional` for transactions. Do not use ORM mapping.
5. **Database connection management is framework-managed.** Remove the original `DBConnectionManager` Singleton holding a shared live JDBC Connection. Use Spring's configured `DataSource`/pool and transaction manager. A single live `Connection` must not be shared across operations or threads.
6. **Current event registration count is derived.** Count `CONFIRMED` registrations. Do not keep a second manually updated `registered_count` column.
7. **Event approval is explicit.** New events are `PENDING_APPROVAL`; students register only for `APPROVED` events.
8. **Backend is authoritative.** Hiding buttons in Swing is UX only; every protected API endpoint must enforce roles and ownership server-side.
9. **No secrets in source control.** Database password and JWT secret come from environment/local ignored configuration.
10. **JSON field names and status strings are API contracts.** Do not rename without review.

## Not permitted without team approval

- JPA, Hibernate or Spring Data repositories
- Direct Swing-to-MySQL connections
- A separate frontend framework such as React
- A different authentication approach, such as sessions instead of JWT
- Additional roles or role spellings
- Schema/endpoint/DTO changes made by one feature branch without an approved contract update
