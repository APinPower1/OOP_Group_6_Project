# Data Model and Persistence Contract

The Flyway migration `backend/src/main/resources/db/migration/V1__initial_schema.sql` is the agreed initial schema. Create the empty DB using `database/create_database.sql`. Feature branches must not independently edit schema design. Member 7 owns migration files; module owners request changes through the shared-contract process.

## Tables

### `users`
- Common identity: `id`, `full_name`, `email`, `password_hash`, `department`, `role`, `enabled`, `created_at`, `updated_at`.
- `role` is exactly one of `STUDENT`, `ORGANIZER`, `FACULTY`, `ADMIN`.
- Email is unique and case-insensitive under the database collation.
- Account deletion is not a normal UI operation; Admin disables an account to preserve history.

### Role profile tables

- `student_profiles(user_id, roll_number, semester, batch)`
- `organizer_profiles(user_id, designation)`
- `faculty_profiles(user_id, employee_id, designation)`
- `admin_profiles(user_id, admin_level)`

Each role profile is one-to-one with `users`. The UserService must ensure the profile type matches `users.role`; SQL foreign keys alone cannot enforce that subtype/role invariant. A role change must update the profile records transactionally and must not leave a profile for the old role.

### `events`
- Contains title, description, venue, start/end time, capacity, status, creator, reviewer/reason and timestamps.
- Valid statuses: `PENDING_APPROVAL`, `APPROVED`, `REJECTED`, `CANCELLED`.
- New proposals are `PENDING_APPROVAL`.
- `registered_count` is intentionally **not persisted**. Use a DAO count query for `CONFIRMED` registrations.
- Venue overlap: normalized venue equal and time ranges overlap. Recheck during approval.

### `registrations`
- Foreign keys to `student_profiles.user_id` and `events.id`.
- **Student foreign key target:** `fk_registration_student` references `student_profiles.user_id`, not `users.id`. A registration therefore requires that the student has a `student_profiles` row; a user without a student profile cannot be registered, and the database will reject such a row. This enforces the role-profile split at the persistence level and is the database half of the invariant that the role-specific profile must match `users.role` (see "Role profile tables").
- Valid statuses: `CONFIRMED`, `CANCELLED`.
- Unique `(student_id, event_id)` prevents duplicate rows.
- Re-registering after cancellation reactivates the same row and gives a new ticket number.
- A `CONFIRMED` registration consumes one seat; a `CANCELLED` registration does not.

### `clubs` / `club_members`
- `clubs` holds name, description, category and active flag.
- `club_members` is the many-to-many association and includes join date/status.
- **Student foreign key target:** `fk_club_membership_student` references `student_profiles.user_id`, not `users.id`. Club membership therefore requires an existing `student_profiles` row.
- Unique `(club_id, student_id)`; a prior `LEFT` row is reactivated when the student joins again.

### `announcements` / `notifications`
- Announcement has title, body, creator, audience (`NULL` means all enabled users; otherwise one of the four roles), status and publish timestamp.
- Publishing creates one notification per currently enabled user matching the audience (`notification_type=ANNOUNCEMENT`). Event cancellation creates `notification_type=EVENT_CANCELLED` rows targeted to confirmed registrants before their registrations are cancelled. Each row includes a title/message so the client can display it without depending on a deleted or archived announcement. This is a simple in-app notification, not email/push.
- Notifications are individual rows with recipient, announcement, creation time and optional read time.
- Archiving an announcement does not delete notification history.

**MySQL NULL behavior (important):** In MySQL, `NULL` values are **distinct** in a `UNIQUE` index. This is intentional in V1:
- `uq_notification_recipient_announcement (recipient_user_id, announcement_id)` prevents duplicate announcement notifications for the same recipient/announcement, but `EVENT_CANCELLED` rows have `announcement_id` `NULL`, so multiple event-cancel notifications do not collide on this index.
- `uq_notification_recipient_event_type (recipient_user_id, event_id, notification_type)` prevents duplicate event-cancel notifications for the same recipient/event/type, but `ANNOUNCEMENT` rows have `event_id` `NULL`, so they do not collide here.
`ck_notification_target` enforces which column is populated per `notification_type`, so each unique index guards the cases it is meant to protect. Do not rely on `NULL` being treated as equal when reasoning about these constraints.

### `feedback`
- Rating is integer 1–5, comment optional, timestamp set by server.
- Unique `(student_id, event_id)`.
- **Student foreign key target:** `fk_feedback_student` references `student_profiles.user_id`, not `users.id`. The database requires an existing `student_profiles` row for a student to submit feedback.
- Student must have a confirmed registration and the event's `end_time` must be in the past.

## Relationship summary

```text
users 1──0..1 student_profiles
users 1──0..1 organizer_profiles
users 1──0..1 faculty_profiles
users 1──0..1 admin_profiles
users(organizer) 1──* events (created_by)
users(faculty/admin) 1──* events (reviewed_by, optional)
student_profiles 1──* registrations *──1 events
student_profiles 1──* club_members *──1 clubs
users 1──* announcements (created_by)
announcements 1──* notifications *──1 users (recipient)
student_profiles 1──* feedback *──1 events
```

## SQL and JDBC rules

- Use `JdbcTemplate`/prepared parameters for all SQL values.
- No concatenation of form input into SQL.
- Put query logic in module-specific DAO classes.
- Every query has explicit selected columns; avoid `SELECT *` in production DAOs when possible.
- Convert SQL rows to DTO/domain objects using `RowMapper` or named mappers.
- Handle generated IDs explicitly using `KeyHolder` or insert-then-select strategy.
- Translate known database constraint errors to stable domain/API errors.
- Do not use a static/shared live `Connection`; the framework-managed `DataSource` and transaction manager handle lifecycle.
- Use InnoDB foreign keys and unique constraints even when Java also validates the rule.
