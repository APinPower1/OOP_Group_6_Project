# Viva Preparation Guide — Student Portal with Event Management System

Everything you need for the OOP viva: architecture talking points, your module
(Member 3 — Events) deep-dive, OOP principles mapped to real code, database/
transaction explanations, likely questions with answers, and a demo script.

---

## 1. Project in 60 seconds (open with this)

> "We built a **role-based college Student Portal** with an Event Management System.
> It is a **client–server** application: a **Java Swing desktop client** on the user's
> machine talks to a **Spring Boot REST API** over HTTP/JSON, and only the server
> connects to **MySQL 8.4** using JDBC. Authentication is **JWT-based**. The system
> supports four roles — Student, Organizer, Faculty and Admin — and covers event
> proposal → approval → registration → cancellation/feedback workflows with
> transactional safety against overbooking and duplicate registrations."

**Key point examiners often probe:** the Swing client is NOT allowed to touch the
database. Every request goes through the API where Spring Security checks the JWT
and the service layer re-checks role + ownership. UI button-hiding is convenience
only; the backend is authoritative.

---

## 2. Architecture (the client–server model)

```text
 User's machine                      Server machine
┌────────────────────┐  HTTP/JSON  ┌─────────────────────────┐  JDBC  ┌───────┐
│  Java Swing GUI    │ ──────────► │  Spring Boot 3.5 (Java 21)│ ─────► │ MySQL │
│  (Views + client   │ ◄────────── │  Controller → Service →   │ ◄───── │ 8.4   │
│   controllers)     │             │  DAO (JdbcTemplate/SQL)   │        └───────┘
└────────────────────┘             └─────────────────────────┘
        JWT in memory                    JWT issued/verified here
```

**Is there a "console app on the server"?** The server is *started* from a
terminal (`./gradlew :backend:bootRun`) but it is **not a console application** —
it is an HTTP server that waits for JSON requests on port 8080 and answers with
JSON. The "console" is just how you launch it. The user-facing program is the
Swing desktop app (`./gradlew :client:run`). So the model is:

- **Server tier:** Spring Boot REST API + MySQL (headless, runs anywhere).
- **Client tier:** Swing desktop GUI (distributed client — same machine in dev,
  different machines in real deployment).

### Layering (one direction of dependency only)

```text
Swing View → Swing controller/state → API client (HTTP) → REST Controller
    → Service (business rules + @Transactional) → DAO (parameterized SQL) → MySQL
```

Rules we enforced:
- Controllers contain no SQL and no business logic.
- DAOs contain no authorization decisions and no UI logic.
- Services own role/ownership checks and transactions.
- All SQL uses `?` placeholders via `JdbcTemplate` — never string concatenation.

---

## 3. Technology stack (know the versions)

| Layer | Technology | Version |
|---|---|---|
| Language | Java (records, switch expressions, text blocks) | 21 (LTS) |
| Server framework | Spring Boot (Web, Security, JDBC, Validation) | 3.5.16 |
| Database | MySQL, InnoDB engine | 8.4 LTS |
| DB access | Spring `JdbcTemplate` (plain JDBC) — **no JPA/Hibernate/Spring Data** | — |
| Schema mgmt | Flyway migrations (`V1__initial_schema.sql`) | — |
| Auth | JWT (HS256), stateless filter chain | — |
| Client UI | Java Swing | — |
| Build | Gradle multi-project (wrapper pinned) | 8.14.3 |
| Tests | JUnit 5 + MockMvc + real-MySQL integration tests | — |

Why no Hibernate? The course requires demonstrating raw JDBC, SQL constraints,
`PreparedStatement` parameters and hand-written transactions — an ORM would hide
all of that.

---

## 4. My module: Event Management (Member 3) — expect questions here

### 4.1 What it does

Full lifecycle of an event proposal:

```text
Organizer creates ──► PENDING_APPROVAL ──approve──► APPROVED ──cancel──► CANCELLED
        ▲                    │                        │
        │    reject ──► REJECTED                     │
        └──── edit (owner only, resubmits) ──────────┘
```

### 4.2 Files I wrote (backend/src/main/java/edu/mec/studentportal/event/)

| File | Role (design pattern) |
|---|---|
| `EventController.java` | **Controller (MVC)** — thin; maps HTTP ↔ service calls, derives user id from JWT principal |
| `EventService.java` | **Service** — all business rules, role/ownership checks, `@Transactional` cancel |
| `EventDAO.java` | **DAO** — every SQL statement, `RowMapper`s, venue-conflict query |
| `Event.java`, `EventStatus.java` | **Domain model** — immutable record + enum |
| `EventRequest.java`, `EventResponse.java`, `RejectRequest.java` | **DTOs** — request validation + response shaping; never expose DB internals |
| `EventErrorCodes.java` | Stable error codes for the client (`VENUE_CONFLICT`, etc.) |
| `EventCancellationNotifier.java` | **Interface** (abstraction) for cancellation notifications |
| `JdbcEventCancellationNotifier.java` | Concrete notifier writing `EVENT_CANCELLED` rows in the same transaction |

Tests: `EventServiceTest` (27 business-rule tests) + `EventControllerTest`
(13 HTTP-contract tests) against a real MySQL database (they self-skip if MySQL
is unreachable so CI stays green).

### 4.3 Business rules I enforce (say these confidently)

1. **New proposals are always `PENDING_APPROVAL`** — set by the server, never by the client.
2. **Only the owner organizer edits their own `PENDING_APPROVAL`/`REJECTED` proposal**;
   an edit clears old review metadata and resubmits to pending. Approved events are
   *never* edited — cancel and create a new proposal.
3. **Faculty/Admin approve or reject; rejection requires a reason** (`400` if blank).
4. **Students only see `APPROVED` events**; organizers see own proposals + approved;
   Faculty/Admin see everything. Enforced in SQL and in the service — not in the UI.
5. **Venue conflict:** normalized venue equality (`LOWER(TRIM(venue))`) + overlapping
   time ranges (`existing.start < new.end AND existing.end > new.start`). Only
   pending/approved events occupy a venue. **Re-checked at approval time** because a
   clash may appear after submission.
6. **Capacity validation:** positive integer; cannot drop below the current confirmed
   registration count on edit.
7. **No stored `registered_count` column.** Every response computes
   `registeredCount = COUNT(*) FROM registrations WHERE event_id=? AND status='CONFIRMED'`
   — single source of truth, no drift.
8. **Cancellation is one transaction:** flip event to `CANCELLED` → create
   `EVENT_CANCELLED` notification per confirmed registrant → cancel all confirmed
   registrations via Member 4's `RegistrationService.cancelAllConfirmedForEvent`
   (Spring `REQUIRED` propagation joins the same transaction/connection). Any failure
   rolls back everything — no "cancelled event with live registrations".

### 4.4 Star code example — the cancellation transaction

Talking point: *"This is the cross-module transaction. The event row is locked with
`SELECT ... FOR UPDATE` so a concurrent approve/cancel serializes; registrant ids are
snapshotted for notifications before registrations are cancelled (per the data-model
contract); and because Member 4's method is `@Transactional` with default REQUIRED
propagation, it joins MY transaction instead of opening a second connection — one
commit for event + registrations + notifications."*

File: `EventService.cancel(...)` — mention `lockById` (FOR UPDATE), the permission
matrix (owner organizer or admin only), and the INVALID_EVENT_STATE guard.

### 4.5 Star SQL example — venue conflict query

```sql
SELECT ... FROM events
WHERE LOWER(TRIM(venue)) = LOWER(TRIM(?))
  AND start_time  < ?          -- existing starts before new one ends
  AND end_time    > ?          -- existing ends after new one starts
  AND status IN ('PENDING_APPROVAL','APPROVED')
  AND id <> ?                  -- exclude the event being edited/approved
```

Talking point: *"Strict inequalities mean back-to-back events — one ending exactly
when the next starts — do NOT clash. Normalization means ' auditorium a ' and
'AUDITORIUM A' are the same venue. Rejected and cancelled events release the venue."*

---

## 5. OOP principles — mapped to concrete code

| Principle | Where it appears in our project |
|---|---|
| **Encapsulation** | Records (`Event`, `Registration`) hide internals behind accessors; DAO SQL is private to the DAO; `SessionContext` keeps the JWT in memory, not exposed to views directly. |
| **Abstraction** | `EventCancellationNotifier` interface — the event service depends on the *abstraction*; the JDBC implementation (or Member 6's future service) is swappable. DAO abstraction: services never see SQL strings. |
| **Inheritance** | Test hierarchy: `EventServiceTest`/`EventControllerTest` **extend** `EventIntegrationTestSupport`, inheriting fixtures (`createStudent()`, `insertEventRow()`); Swing views share a common base frame. |
| **Polymorphism** | `switch` on role/status (behavior varies by type); `RowMapper<Event>` lambda vs `RowMapper<Registration>` — same interface, different mapping; multiple `ApiExceptionHandler` methods chosen per exception type. |
| **Interfaces** | `EventCancellationNotifier`; Spring's `SecurityFilterChain` beans; JDBC's `RowMapper`, `PreparedStatement` used behind `JdbcTemplate`. |
| **Collections** | `List<Event>` filter results, `Map<Long,Integer>` batch counts, `ArrayList` of SQL clauses assembled in `EventDAO.search`. |
| **Custom exceptions** | `ApiException` carries `HttpStatus` + stable `code` + safe message; domain codes like `VENUE_CONFLICT`, `EVENT_FULL`, `DUPLICATE_REGISTRATION`. |
| **MVC** | View (Swing / MockMvc) → Controller (`EventController`) → Model (`EventService` + `EventDAO` + DB). |
| **DAO pattern** | Every module: `*Controller` → `*Service` → `*DAO`; no SQL outside DAOs. |

---

## 6. Database design (top viva slide)

Eleven tables, all InnoDB with foreign keys and CHECK constraints. Highlights:

- `users` + four one-to-one role profile tables (`student_profiles`,
  `organizer_profiles`, `faculty_profiles`, `admin_profiles`) — subtype modeling via
  composition; the service must keep `users.role` consistent with the profile row.
- `events.status` CHECK ∈ {PENDING_APPROVAL, APPROVED, REJECTED, CANCELLED};
  `end_time > start_time` and `capacity > 0` enforced in the DB *and* the service
  (belt and braces — DB constraints are the last line of defense).
- `registrations`: UNIQUE `(student_id, event_id)` prevents duplicate registrations
  at the schema level; UNIQUE `ticket_no`; `CONFIRMED` rows consume seats,
  `CANCELLED` rows free them.
- `notifications`: UNIQUE `(recipient_user_id, event_id, notification_type)` makes
  duplicate EVENT_CANCELLED notifications impossible even under races.
- **No `registered_count` column** — counts are always queried.

---

## 7. Transactions & concurrency (the "hard" questions)

**Q: How do you prevent overbooking when two students register for the last seat at
the same instant?**
> Member 4's `register()` runs in one transaction and locks the event row with
> `SELECT ... FOR UPDATE`. The second request blocks until the first commits, then
> re-reads the locked row, recounts CONFIRMED registrations, and returns
> `409 EVENT_FULL` if the seat is gone. The DB unique constraint on
> `(student_id, event_id)` is the final guard against duplicates; a racing insert
> that still hits it is mapped to `409 DUPLICATE_REGISTRATION`.

**Q: What does `@Transactional` propagation REQUIRED mean?**
> The default: if a transaction already exists on the current thread/connection,
> the method joins it; otherwise it starts a new one. That is exactly how event
> cancellation calls `cancelAllConfirmedForEvent` — one connection, one commit.

**Q: Why is `registered_count` not a column?**
> Two writers (registration service, cancellation) would have to update it in lock
> step — classic drift bug. A `COUNT(*)` query on `CONFIRMED` rows is always correct.

---

## 8. Security (two layers)

1. **Spring Security (transport layer):** stateless JWT filter validates signature
   and expiry; `401 UNAUTHENTICATED` without a valid token; role authorities route
   endpoint access.
2. **Service layer (domain layer):** reloads the user's role + enabled flag from
   the DB for *every* operation (so a disabled account dies immediately even with a
   live token), and re-checks ownership (e.g., organizer can edit only events where
   `created_by = me`).

Security example list for the examiner:
- Student cannot create/approve events → `403 FORBIDDEN`.
- Organizer cannot approve their own event → `403`.
- Cancel: owner organizer or Admin only → `403` otherwise.
- Roster view: owner organizer or Faculty/Admin.
- Secrets (JWT key, DB password) live only in environment variables — never in
  source or the Swing client.

---

## 9. Likely viva questions — quick answers

**Why Spring Boot and not plain servlets?** Auto-configuration, embedded Tomcat,
declarative security, `JdbcTemplate` with connection pooling, and a test-friendly
context — while still letting us write plain JDBC SQL as the course requires.

**What is a DTO vs a domain object?** Domain (`Event`) mirrors the table and stays
inside the backend. DTOs (`EventRequest`/`EventResponse`) are the wire contract —
they carry validation annotations and deliberately omit server-controlled fields so
clients cannot forge status/ids.

**Why records?** Immutable, compact, automatic equals/hashCode/toString — ideal for
DTOs and domain rows that are never mutated after mapping.

**How does Flyway help?** `V1__initial_schema.sql` runs once at startup; every
developer/environment gets the identical schema; schema changes are versioned in
git instead of hand-run scripts.

**What is the `KeyHolder` used for?** After `INSERT INTO events ...`, MySQL's
generated id is retrieved so the service can immediately re-read and return the
full row (insert-then-select).

**What happens on event cancellation for a student's registration?** It becomes
`CANCELLED` (soft delete — history preserved), the seat frees up, and the student
gets an in-app `EVENT_CANCELLED` notification — all committed atomically.

**Why MockMvc + real MySQL in tests, not mocks?** `FOR UPDATE` row locks and
unique-constraint races cannot be faked with an embedded DB or mocks; the tests
skip gracefully when MySQL is unavailable.

**How would this scale beyond one server?** Stateless JWT allows multiple API
instances behind a load balancer; MySQL is the shared source of truth; the Swing
client only needs the base URL changed.

---

## 10. Demo script (5 minutes)

Setup (before the demo): backend running (`./gradlew :backend:bootRun`), DB created,
Swing client running (`./gradlew :client:run`), demo users seeded.

1. **Login as Organizer** → show dashboard → **Create Event**
   ("Tech Fest Workshop", Auditorium A, future date, capacity 50) →
   show it appears as `PENDING_APPROVAL` with Edit/Cancel buttons.
2. **Try venue clash:** create a second event, same venue, overlapping time →
   show the friendly `VENUE_CONFLICT` error dialog. This proves server-side rules.
3. **Login as Faculty (second client instance or logout/login)** → Review queue
   shows the pending event → open details → **Approve** → status flips to APPROVED.
   (Optionally reject another proposal with a reason to show that flow.)
4. **Login as Student** → event feed shows ONLY the approved event (pending one is
   invisible) → **Register** → seat count goes 49/50, ticket number shown.
5. **Back to Organizer** → open roster → student visible → **Cancel the event** →
   event shows CANCELLED, roster seats released.
6. **Login as Student again** → "My Registrations" shows CANCELLED + (after Member 6
   integrates notifications) an EVENT_CANCELLED notification. Show that the student
   can re-register only if a new approved event exists — cancelled events never
   accept registrations.

Backup lines if something breaks live:
- "The same flows are covered by 40 automated integration tests against real MySQL —
  I can show the green test run." → run `./gradlew :backend:test`.

---

## 11. Your 30-second "what I built" pitch

> "I built the event management module: proposal CRUD with server-side validation,
> a role-scoped event feed, faculty approval with a venue double-booking re-check,
> rejection with mandatory reasons, and an atomic cancellation that coordinates the
> registration module and student notifications in a single database transaction.
> It's 8 main classes and 40 integration tests; all SQL is parameterized JDBC in the
> DAO, all authorization is re-verified in the service against the database, and
> seat counts are always live queries — never a stored counter."
