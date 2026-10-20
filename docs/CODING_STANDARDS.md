# Coding Standards

## Java

- Use Java 21 language/toolchain unless Member 1 commits another team-wide baseline before feature work.
- Use package root `edu.mec.studentportal` unless changed centrally before implementation.
- Prefer constructor injection, clear names, small methods and immutable request/response DTOs where practical.
- Keep domain objects free of JDBC and Swing dependencies.
- Validate at API boundaries and enforce business invariants in services. Database constraints are a second line of defense.
- Avoid static mutable state. In particular, never share a live JDBC `Connection` in a Singleton.
- Use enums for role/event/registration/membership statuses; serialize the exact agreed uppercase values.
- Use `LocalDateTime` consistently for campus-local event time and configure application/database timezone consistently.
- Do not catch exceptions and silently ignore them. Translate expected domain errors to API error codes; log unexpected errors with a correlation-friendly message but no credentials/tokens.

## JDBC

- Backend only. Use `JdbcTemplate` and parameter placeholders.
- Put SQL in `*Dao`/`*Repository`-named DAO classes; do not use Spring Data repositories.
- Use service-level `@Transactional` for multi-query operations.
- Use explicit columns and `RowMapper`s; don't return raw `ResultSet` outside DAO methods.
- Do not build `IN`/`ORDER BY` fragments from unchecked user strings. Use allow-lists if dynamic sorting is needed.

## REST/API

- Request/response JSON names match `API_CONTRACT.md` exactly.
- Use request DTOs; do not deserialize into database/domain entities directly.
- Controller methods are thin.
- All role/ownership checks run on backend.
- Return standardized error bodies and stable codes. Shared error types live in `edu.mec.studentportal.api`; throw `ApiException` for expected domain failures.
- Use server-derived user IDs for student actions; ignore/reject a client-supplied student ID.

## Swing

- Build UI and update Swing components on the Event Dispatch Thread.
- Run HTTP calls off the EDT with `SwingWorker` or shared equivalent.
- Use one shared API client for base URL, bearer header, JSON parsing and timeout handling.
- Show understandable messages; never display stack traces, SQL text or tokens.
- Clear token/user state on logout, token expiry or disabled-account response.

## Git and files

- One feature branch per task; small commits.
- Run relevant tests before push.
- Never edit a module outside your ownership without asking its owner.
- No dependency, package-root, API or schema change without the shared-contract process.
