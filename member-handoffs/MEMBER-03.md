# Member 03 — Event Management, Scheduling and Approval

## Mission
Implement Event model/DTOs, event DAO/service/controller, event proposal CRUD, venue-overlap checks, approval/rejection and cancellation coordination.

## Own
- `event/**` except registration workflow files owned by Member 4
- Event unit/API integration tests

## Contract requirements
- New event proposal status is server-set `PENDING_APPROVAL`.
- Organizer can edit only own pending/rejected proposal. Edited proposal returns to pending approval.
- Faculty/Admin can approve/reject pending proposal; rejection requires reason.
- Student feed exposes only approved events that meet visibility rules.
- `Event` has title, description, venue, `startTime`, `endTime`, capacity and status.
- End time must be after start time; capacity must be positive.
- Venue conflict uses normalized venue equality and overlapping time ranges. Re-check at approval time.
- Approved events are cancelled, not edited. Cancellation must coordinate one transaction with Member 4's registration service and Member 6's notifications.
- Do not store `registered_count`; use registration count query from Member 4/DAO contract to populate response.

## Service/DAO split
Controller validates DTO shape; EventService owns authorization/transition rules; EventDAO contains parameterized SQL. Do not put SQL in controller. For event cancellation, agree on a service boundary that safely updates event + registrations + notifications in one transaction, with Member 4 and Member 6.

## Tests
- CRUD and permissions; invalid time/capacity.
- approve/reject transitions and reason requirement.
- venue clash positive/negative/overlap-boundary tests.
- organizer cannot edit another user's event or an approved event.
- cancellation transaction and invalid state attempts.
