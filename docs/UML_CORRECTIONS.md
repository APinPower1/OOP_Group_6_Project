# UML Corrections and Clarifications

This is the approved implementation interpretation of the updated UML. It does not discard the original OOP intent; it resolves ambiguities so nine parallel AI-assisted implementations produce compatible code.

## Accepted corrections

1. **Client/server split:** Swing is the View/client; Spring Boot REST API is the server. JDBC/MySQL is backend-only because the chosen stack includes Spring Boot and JWT.
2. **MVC remains:** use Swing MVC on the client and Controller → Service → DAO on the backend. Service layer is explicit because the sequence diagram already uses `EventService`.
3. **Four user subclasses:** abstract `User` with `Student`, `Organizer`, `Faculty`, `Admin`. MySQL persists base user identity in `users`, role-specific fields in one-to-one profile tables. Service ensures role/profile consistency.
4. **Event status:** `PENDING_APPROVAL`, `APPROVED`, `REJECTED`, `CANCELLED`; new event is pending, students see/register only for approved events.
5. **Scheduling:** `Event` has both start and end timestamps. Venue conflicts are interval overlaps and are rechecked at approval time.
6. **Seat count:** remove persistent `registeredCount` as a manually maintained database column; derive count from `CONFIRMED` registrations. Response DTO may still expose `registeredCount`/`availableSlots` as calculated values.
7. **Registration safety:** service transaction locks the event row (`SELECT ... FOR UPDATE`), checks state/capacity and duplicate, writes registration, then commits. DB unique constraint protects duplicates. Cancelled row can be reactivated to permit a new attempt, with a new ticket number.
8. **Database connections:** retire Singleton storing one live `Connection`. Spring-managed `DataSource`/connection pool and transaction manager are thread-safe and work with `@Transactional`. If the rubric requires Singleton, demonstrate it for a stateless utility/configuration component, not a shared live JDBC Connection.
9. **Club association:** explicit many-to-many table `club_members`, unique pair, `ACTIVE`/`LEFT` state.
10. **Feedback:** rating constraint 1–5, one feedback per student/event, event must have ended, student must have a confirmed registration.
11. **Announcements/notifications:** announcement publish persists the announcement and creates in-app notification rows for target users. Event cancellation can create a typed notification linked to the event. No email/SMS/live push.
12. **Custom exceptions:** `EventFullException`, `DuplicateRegistrationException`, `VenueConflictException`, `InvalidEventStateException`, `DatabaseOperationException` (or equivalent agreed names) map to stable API error codes. Do not expose raw SQL exceptions.
13. **Time handling:** use one consistent campus-local timezone setting. JSON format: `yyyy-MM-dd'T'HH:mm:ss`, MySQL fields `DATETIME`; do not mix local and UTC representations silently.

## Improvements retained from original UML

- `Notifiable` should describe an explicit notification contract. Recommended: Announcement provides a notification payload/message, while a `NotificationService` performs fan-out and persistence. Do not make a POJO open a DB connection or directly send UI events.
- `Schedulable` should refer to a defined `TimeSlot` value object or be simplified to a scheduling validation operation. Avoid a method returning an undefined type.
- Keep `EventService` as the owner of scheduling and registration use cases; use `EventDAO` / `RegistrationDAO` for SQL.
- Keep `Feedback` associated with both Student and Event in the domain model even though the database stores foreign keys.
- The role/use-case diagram should make login a separate use case; viewing public/approved listings and performing authenticated actions should be distinguished.

## Explicit business defaults

- Only approved upcoming events accept registrations.
- Event capacity is a positive integer; confirmed registrations may never exceed it.
- Approved events are cancelled, not edited. Proposal edits resubmit for approval.
- A venue collision is same normalized venue plus overlapping time intervals.
- A student can cancel registration before event start; a cancelled event cancels all confirmed registrations.
- No payment, QR scan, or check-in tracking.
- User accounts are disabled rather than physically deleted in normal use to preserve referenced history.
