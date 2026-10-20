# Decision Log

| ID | Decision | Status | Rationale |
|---|---|---|---|
| D-001 | Swing client communicates with Spring Boot REST API; only backend accesses MySQL | Approved for this pack | Makes JWT and server-side security meaningful; keeps DB credentials out of desktop client. |
| D-002 | JDBC via Spring JDBC/`JdbcTemplate`; no JPA/Hibernate/Spring Data | Approved for this pack | Matches the stated JDBC CRUD requirement. |
| D-003 | Four roles: STUDENT, ORGANIZER, FACULTY, ADMIN | Approved | Matches updated UML. |
| D-004 | Event status uses PENDING_APPROVAL, APPROVED, REJECTED, CANCELLED | Approved | Makes approval/moderation lifecycle explicit. |
| D-005 | Registration count is derived from CONFIRMED registration rows | Approved | Avoids a duplicated count that can drift from the registrations table. |
| D-006 | Transactional seat booking uses SELECT ... FOR UPDATE + unique student/event constraint | Approved | Prevents concurrent overbooking and duplicate rows. |
| D-007 | Spring-managed DataSource/transactions replace shared-Connection Singleton | Approved | Shared live Connection is unsafe for concurrent operations. |
| D-008 | MySQL role-specific profile tables reflect User subclasses | Proposed default in migration | Normalizes role-specific attributes and matches the OOP model; service keeps profile and role consistent. |
| D-009 | Local event timestamps use one configured campus timezone | Proposed default | Avoids inconsistent display and comparison; default Asia/Kolkata. |
| D-010 | Shared baseline: Java 21, Spring Boot 3.5.16, Gradle Wrapper 8.14.3, MySQL 8.4; Spring Security/Nimbus JWT; Jackson via Boot BOM | Approved for this pack | Reduces build/dependency drift across AI-assisted branches. Wrapper files must be generated and committed by Member 1 once. |

Add future decisions at the bottom with date, proposer, approvers and impacted contracts.
| D-011 | Shared API error envelope and generic HTTP error codes; feature failures use `ApiException` | Pending affected-owner review before merge | 2026-10-20; proposer: Member 1; affected API/module owners: pending; impacts `API_CONTRACT.md`, `edu.mec.studentportal.api`, and bootstrap security error responses. |
