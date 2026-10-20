# Member 05 — Clubs and Membership

## Mission
Implement club listing/details, Admin club management and Student join/leave behavior.

## Own
- `club/**`
- Club/membership tests

## Contract requirements
- Tables: `clubs`, `club_members`.
- Admin creates/updates/deactivates clubs; no hard delete in normal workflow.
- Students can view active clubs and join/leave them.
- Only students can change their own membership; client cannot submit another student ID.
- Each `(club_id, student_id)` pair has one row. `ACTIVE` means joined, `LEFT` means no longer active.
- Rejoining updates existing membership row to `ACTIVE` and resets/updates `joined_at` according to agreed DAO method; do not insert duplicates.
- Inactive club cannot be joined.
- Club member roster is Admin-only in the default permissions.

## Tests
- Create/update/deactivate restricted to Admin.
- Student joins/leaves own club.
- Duplicate join idempotently rejects or returns current membership consistently.
- Rejoin reactivates existing row.
- Inactive club join rejected.
- Foreign-key/unique constraints work.
