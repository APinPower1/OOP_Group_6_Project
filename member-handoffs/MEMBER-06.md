# Member 06 — Announcements, Notifications and Feedback

## Mission
Implement announcement publishing/audience filtering, in-app notifications, and event feedback/ratings.

## Own
- `announcement/**`, `notification/**`, `feedback/**`
- Related services/DAOs/controllers/DTOs and tests

## Announcements/notifications
- Organizer/Admin may publish announcements.
- `audienceRole = null` targets all enabled users; otherwise target exactly one role.
- Persist announcement and create one notification row per matching user in one transaction. Notification rows use type `ANNOUNCEMENT` and include title/message; event cancellation notifications use type `EVENT_CANCELLED` and are coordinated with Member 3/4.
- Each recipient sees only their notifications; user can mark only their own notification read.
- Archive instead of hard-delete; retain notification history.
- No email/SMS/live push.
- Keep `Notifiable` behavior clean: Announcement can expose a notification payload/message; a NotificationService should do recipient selection and persistence. Domain object must not open a DB connection.

## Feedback
- Rating 1–5, optional comment.
- Only students with a confirmed registration can submit, and only after event `endTime`.
- One row per student/event; unique constraint backs application validation.
- Organizer views feedback only for own events. Faculty/Admin can view permitted records/reports.
- A general signed-in viewer may see event average/count only, not private student details.

## Tests
- Role and audience notification creation.
- No duplicate notification for same recipient/announcement.
- Cannot read/mark another user's notification.
- Feedback bounds, early submission, no registration, duplicate submission.
- Event owner and role access checks.
- Announcement/notification transaction rolls back consistently on failure.
