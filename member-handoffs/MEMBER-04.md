# Member 04 — Event Registration, Ticketing and Capacity Safety

## Mission
Implement the most critical business transaction: registering/cancelling students while preventing duplicate registration and event overbooking.

## Own
- `registration/**`
- Registration DAO, service, controller/DTOs and concurrency tests

## Required transaction algorithm
1. Start service-level transaction.
2. Lock target event row using `SELECT ... FOR UPDATE` inside the same transaction/connection.
3. Confirm event exists, status is `APPROVED`, event start time is in the future and user is a Student.
4. Check whether a row already exists for `(student_id, event_id)`.
5. Count only `CONFIRMED` registrations.
6. If full, return `409 EVENT_FULL`; if already confirmed, return `409 DUPLICATE_REGISTRATION`.
7. Insert a registration or reactivate its cancelled row, generate a ticket number, commit.
8. Calculate count/available slots from confirmed rows in response.

The DB unique constraint is mandatory. Do not rely on an in-memory `HashSet` as the source of truth.

## Cancellation
Student can cancel only their own confirmed registration before event start. A cancellation changes status to `CANCELLED`; it does not hard-delete. Reactivation after cancellation is allowed only if event is still open and a seat exists; reuse row but regenerate ticket number and timestamp. Event cancellation from Member 3 must cancel all confirmed registrations transactionally.

## Tests
- Normal registration/ticket.
- Duplicate attempt.
- Event full.
- Pending/rejected/cancelled/past event.
- Student attempts another student's cancellation.
- Cancellation frees slot; reactivation consumes one slot.
- Concurrent last-seat test: more simultaneous requests than remaining capacity, and confirmed count never exceeds capacity.
- SQL unique-constraint race handling maps to stable duplicate error.

## Do not
- Add a `registered_count` database column.
- Split the critical transaction across several independently managed connections.
- Treat UI button disabling as correctness protection.
