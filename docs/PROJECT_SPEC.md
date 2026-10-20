# Project Specification

## 1. Problem and solution

Campus events, announcements and club membership are often tracked through disconnected channels and manual lists. This application consolidates event scheduling, approval, registration, capacity enforcement, clubs, announcements, notifications and feedback into one role-based desktop portal.

## 2. Actors and responsibilities

| Role | Allowed responsibilities |
|---|---|
| `STUDENT` | View approved events and published announcements; register/cancel own registration; browse clubs; join/leave clubs; view own notifications; submit eligible feedback. |
| `ORGANIZER` | Create event proposals; edit own pending/rejected proposals; cancel own approved events; view registration roster for own events; publish announcements; view feedback for own events. |
| `FACULTY` | Review and approve/reject event proposals; view events and moderation details; view participation summaries. |
| `ADMIN` | Manage accounts and roles; manage clubs; publish/archive announcements; approve/reject events; cancel events if required; view reports and system-level summaries. |

A role is not enough to authorize ownership-sensitive actions. For example, an Organizer can edit only that Organizer's own pending/rejected event proposals.

## 3. Core features

### Authentication and accounts
- Login with email and password.
- Passwords are stored as BCrypt hashes.
- JWT access token is returned by backend on successful login.
- Four roles; backend role and ownership checks on every protected action.
- Admin can enable/disable accounts and assign roles.

### Event management and approval
- Event fields: title, description, venue, start time, end time, capacity, status, creator, reviewer and review reason.
- Creation submits the event as `PENDING_APPROVAL`.
- Faculty/Admin approve or reject with a reason for rejection.
- Approved events are visible to students and can accept registrations only before start time and while capacity remains.
- Validate end time after start time, capacity > 0, venue conflicts and owner permissions.
- Venue overlaps are checked at event creation for useful feedback and rechecked during approval for correctness.
- An event conflict exists when normalized venue matches and `existing.start_time < proposed.end_time AND existing.end_time > proposed.start_time` for another approved event.
- Approved events are cancelled, not silently edited. This preserves the meaning of existing tickets/registrations.

### Event registration
- One registration row per student/event.
- Confirmed registrations count toward capacity.
- Registration validation and write operations occur in one transaction with the event row locked using `SELECT ... FOR UPDATE`.
- Duplicate and full-capacity attempts return stable API errors.
- Cancellation releases capacity. Re-registration, if allowed before event start, reactivates the same row and issues a fresh ticket number.
- A ticket number is an identifier only; there is no payment or payment gateway.

### Clubs
- Admin creates/edits/deactivates clubs.
- Students can join/leave active clubs.
- A student can have at most one membership row per club. Leaving changes status to `LEFT`; joining again reactivates it.
- Admin can view club members. No separate club-leader role is in scope.

### Announcements and notifications
- Organizer/Admin may publish announcements.
- An announcement can target all users or one role.
- When published, notification rows are generated for enabled users matching the audience. Event cancellation also creates notification rows for the event’s confirmed registrants.
- Students can view their notification list and mark notifications read.
- This is basic in-app notification storage; live push/email/SMS are out of scope.

### Feedback and reports
- Rating is an integer from 1 to 5; comment is optional.
- Only a student with a confirmed event registration may submit feedback after event end time.
- Unique student/event feedback prevents repeated submissions.
- Organizers can view feedback for their own events; Faculty/Admin can view it for reporting/moderation.
- Admin can view basic counts: event totals by status, confirmed registrations by event, club membership counts and average feedback rating.

## 4. Explicitly out of scope for MVP

Chat, music preferences, discussion forums, notes/material sharing, profile social features, timetable/calendar synchronization, payments, ticket QR scanning, live notifications, email/SMS notifications and deployment to a public production environment.

## 5. Acceptance target

A Student, Organizer, Faculty and Admin can sign in with seeded/demo accounts and complete their permitted workflows through Swing, with API authorization and MySQL persistence working end to end. All cases in `docs/TEST_PLAN.md` must pass before presentation.
