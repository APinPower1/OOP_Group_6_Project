# Test Plan and Acceptance Checklist

## Required unit tests

### Auth/users
- Correct login returns token and user summary.
- Incorrect credentials return generic `401` without revealing account existence.
- Disabled users cannot log in or continue making authorized requests.
- Every role maps to exactly one matching profile type.
- Admin-only user/role actions reject other roles.

### Events
- Creation always starts as `PENDING_APPROVAL`; client cannot submit `APPROVED` status or fake creator/reviewer.
- Student event feed contains approved events only.
- Approve/reject succeeds only for Faculty/Admin and pending proposals.
- Reject requires a reason.
- Same-venue overlapping approved event is rejected at approval; non-overlapping event is allowed.
- Event end time must be after start time; capacity must be positive.
- Organizer cannot edit another organizer's proposal or an approved event.
- Cancellation transaction cancels registrations and creates notifications.

### Registration (critical)
- Student can register for an approved upcoming event with seats.
- Same student cannot register twice.
- Full event returns `409 EVENT_FULL`.
- Pending/rejected/cancelled/past-start event rejects registration.
- Cancellation before event start releases one seat.
- Cancelled registration reactivation creates a new ticket number and consumes one seat if available.
- Concurrent requests for the last seat cannot overbook capacity.
- SQL unique constraint protects duplicate registration even if two calls race.

### Clubs
- Student joins active club.
- Duplicate join does not make duplicate row.
- Leave changes membership to `LEFT`; rejoin reactivates same row.
- Inactive club cannot be joined.
- Only Admin manages clubs; only Student changes own membership.

### Announcements/notifications/feedback
- Only Organizer/Faculty/Admin publishes announcement; faculty may archive only their own.
- Audience filtering creates notifications only for matching enabled users.
- A user cannot read/mark another user's notification.
- Rating 0 or 6 rejected; optional comment may be blank/null.
- Feedback before event end, without a confirmed registration, or duplicate feedback is rejected.
- Organizer sees feedback for own event only; Faculty/Admin can see permitted reports.

## Integration smoke workflow

1. Start MySQL, create the empty `student_portal` database using `database/create_database.sql`, then start backend so Flyway applies `backend/src/main/resources/db/migration/V1__initial_schema.sql`.
2. Start Spring Boot backend and verify `/api/health`.
3. Log in through Swing as Student; verify token header is attached to protected calls.
4. Browse approved events; try registration; verify ticket displayed and persisted.
5. Cancel registration; verify seat count rises.
6. Log in as Organizer; create proposal.
7. Log in as Faculty; approve proposal; verify it appears for Student.
8. As Organizer, publish an announcement; as Student, verify it and notification.
9. As Admin, create/deactivate a club and review participation reports.
10. After an event's end time in test data, submit feedback and verify rating summary.
11. Try unauthorized actions by calling API directly; verify backend returns `403` even if the Swing button is hidden.
12. Disable a user; verify future requests fail.

## Exit criteria

- All critical registration concurrency tests pass.
- No open P0/P1 bug for authentication, authorization, data loss or seat overbooking.
- Each role can complete permitted workflows through Swing.
- DB credentials/JWT secret are not in source control.
- Member 9 records test evidence and any accepted limitations before presentation.

## Acceptance coverage (member 9)

The scenarios above are additionally covered end-to-end through the **production security
chain** (real login → real JWT → real filter) in
`backend/src/test/java/edu/mec/studentportal/acceptance/` — 21 tests, all passing.
Detailed scenario → test → evidence mapping and the two defects this suite found are in
[ACCEPTANCE_REPORT.md](ACCEPTANCE_REPORT.md).

- Auth/users: ✅ `AuthenticationAuthorizationAcceptanceTest`
- Events (create/approve/reject/overlap/visibility/authority): ✅ `EventLifecycleAcceptanceTest`
- Registration seat math + feedback gating: ✅ `RegistrationAndFeedbackAcceptanceTest`
- Clubs + announcements + admin reports + user management: ✅ `AdminUsersReportsAnnouncementsAcceptanceTest`
- Not duplicated here (covered by unit/integration tests): concurrency races, profile-to-role mapping.

