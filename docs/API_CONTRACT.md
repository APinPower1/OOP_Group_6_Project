# REST API Contract v1

Base path: `/api`. All authenticated endpoints require `Authorization: Bearer <JWT>`. JSON property names are camelCase. The API contract is shared by the backend and Swing client; do not rename fields without an approved change.

## Common conventions

- Successful read: `200 OK`.
- Successful creation: `201 Created`.
- Successful operation with no body: `204 No Content` only where specified.
- Invalid/malformed request: `400 Bad Request`.
- Missing/expired/invalid token: `401 Unauthorized`.
- Authenticated but unauthorized role/ownership: `403 Forbidden`.
- Missing record: `404 Not Found`.
- Duplicate registration, full event, venue overlap, invalid event state: `409 Conflict`.
- Unhandled server error: `500 Internal Server Error`, without stack trace in response.

### Standard error body

```json
{
  "timestamp": "2026-10-18T14:00:00Z",
  "status": 409,
  "code": "EVENT_FULL",
  "message": "No seats are available for this event.",
  "path": "/api/events/12/registrations"
}
```

`code` is stable for client handling. User-facing messages may improve, but existing codes must not change silently. Generic codes emitted by the shared backend handler are `INVALID_REQUEST` (400), `UNAUTHENTICATED` (401), `FORBIDDEN` (403), `NOT_FOUND` (404), `METHOD_NOT_ALLOWED` (405), `CONFLICT` (409), `UNSUPPORTED_MEDIA_TYPE` (415), `INTERNAL_ERROR` (500), and `REQUEST_REJECTED` for other mapped 4xx responses. Domain failures use their specific contract code, such as `EVENT_FULL`. The timestamp is ISO-8601 UTC. Error responses never include stack traces or internal exception details.

Member 1 owns the shared `edu.mec.studentportal.api.ApiErrorResponse` shape and `ApiException` mapping. Feature modules should throw `ApiException` for expected domain failures so they keep the documented HTTP status, stable code, and safe message. Spring Security entry-point and access-denied failures use the same response body.

## Auth

| Method | Path | Access | Purpose |
|---|---|---|---|
| `POST` | `/auth/login` | Public | Verify credentials; returns JWT + user summary. |
| `GET` | `/auth/me` | Any signed-in role | Return current user/profile summary and current role. |
| `GET` | `/health` | Public | Basic health check. |

Login request:
```json
{ "email": "student@example.edu", "password": "demo-password" }
```
Login response:
```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresInSeconds": 1800,
  "user": { "id": 41, "fullName": "Demo Student", "email": "student@example.edu", "role": "STUDENT" }
}
```
Logout is client-side for MVP: clear the in-memory token and return to login. There is no refresh token or revocation endpoint.

## Events

| Method | Path | Access | Purpose |
|---|---|---|---|
| `GET` | `/events` | Signed-in | Students see approved events; organizers see their own proposals plus approved events; Faculty/Admin can review all statuses. Optional filters: `q`, `from`, `to`, `status` (server restricts what each role may query). |
| `GET` | `/events/{eventId}` | Signed-in | Event details, with role-appropriate visibility. |
| `POST` | `/events` | Organizer | Create proposal; status is set server-side to `PENDING_APPROVAL`. |
| `PUT` | `/events/{eventId}` | Owner Organizer, if pending/rejected | Update proposal and resubmit it as `PENDING_APPROVAL`. Client may not set creator/status/review metadata. |
| `POST` | `/events/{eventId}/approve` | Faculty/Admin | Approve pending event after validating venue and time clash. |
| `POST` | `/events/{eventId}/reject` | Faculty/Admin | Reject pending event; body requires `reason`. |
| `POST` | `/events/{eventId}/cancel` | Owner Organizer or Admin | Cancel approved/pending event; transactionally cancel confirmed registrations and notify students. |
| `GET` | `/events/{eventId}/registrations` | Owner Organizer, Faculty/Admin | View registration roster. Return minimal student data only. |

Event creation/update body:
```json
{
  "title": "Open Source Day",
  "description": "Talks and workshops",
  "venue": "Auditorium A",
  "startTime": "2026-11-12T10:00:00",
  "endTime": "2026-11-12T16:00:00",
  "capacity": 120
}
```
Response includes `id`, fields above, `status`, `createdBy`, optional `reviewedBy`, `reviewReason`, timestamps, `registeredCount` (calculated query result), and `availableSlots` (`capacity - confirmed registration count`). Date/time strings use campus-local time; backend and MySQL server timezone must be configured consistently (Asia/Kolkata for development at MEC).

## Registrations

| Method | Path | Access | Purpose |
|---|---|---|---|
| `POST` | `/events/{eventId}/registrations` | Student | Register; transactionally validate approved/open event, lock event row, check duplicate/capacity, create or reactivate registration. |
| `DELETE` | `/events/{eventId}/registrations/me` | Student | Cancel own confirmed registration before event start; releases one seat. |
| `GET` | `/registrations/me` | Student | Return current student's registration history; include confirmed/cancelled status. |

Registration response:
```json
{
  "registrationId": 91,
  "eventId": 12,
  "status": "CONFIRMED",
  "ticketNo": "EVT-...",
  "registeredAt": "2026-10-18T14:00:00"
}
```
Important error codes: `EVENT_NOT_FOUND`, `EVENT_NOT_APPROVED`, `REGISTRATION_CLOSED`, `EVENT_FULL`, `DUPLICATE_REGISTRATION`, `REGISTRATION_NOT_FOUND`, `REGISTRATION_ALREADY_CANCELLED`.

## Clubs

| Method | Path | Access | Purpose |
|---|---|---|---|
| `GET` | `/clubs` | Signed-in | List active clubs. |
| `GET` | `/clubs/{clubId}` | Signed-in | Club details and active member count. |
| `POST` | `/clubs` | Admin | Create club. |
| `PUT` | `/clubs/{clubId}` | Admin | Update club metadata. |
| `POST` | `/clubs/{clubId}/deactivate` | Admin | Deactivate club without deleting memberships/history. |
| `POST` | `/clubs/{clubId}/membership` | Student | Join/reactivate membership. |
| `DELETE` | `/clubs/{clubId}/membership` | Student | Leave club. |
| `GET` | `/clubs/{clubId}/members` | Admin | View membership roster. |

## Announcements and notifications

| Method | Path | Access | Purpose |
|---|---|---|---|
| `GET` | `/announcements` | Signed-in | Students see published announcements matching their role/audience. Staff may see their permitted records. |
| `POST` | `/announcements` | Organizer/Admin | Publish announcement and create recipient notifications transactionally. |
| `PUT` | `/announcements/{id}` | Creator Organizer or Admin, only while draft if drafts are implemented | Edit announcement. MVP can create directly as published if draft is omitted. |
| `POST` | `/announcements/{id}/archive` | Creator Organizer or Admin | Archive; retain history. |
| `GET` | `/notifications/me` | Signed-in | Current user's notifications. |
| `PATCH` | `/notifications/{id}/read` | Recipient only | Mark own notification read. |

Announcement request:
```json
{ "title": "Registration Opens", "content": "Registration is now open.", "audienceRole": null }
```
`audienceRole` is either `null` (all enabled users) or one of `STUDENT`, `ORGANIZER`, `FACULTY`, `ADMIN`.

Notification response objects include `id`, `notificationType`, `title`, `message`, nullable `announcementId`, nullable `eventId`, `createdAt`, and nullable `readAt`. A recipient can only list or mark their own notification rows.

## Feedback and reports

| Method | Path | Access | Purpose |
|---|---|---|---|
| `POST` | `/events/{eventId}/feedback` | Student | Submit one rating/comment after event end, if the student has a confirmed registration. |
| `GET` | `/events/{eventId}/feedback` | Owner Organizer, Faculty/Admin | View feedback records for the event. |
| `GET` | `/events/{eventId}/feedback/summary` | Signed-in | Average rating and count only; no private student identity. |
| `GET` | `/admin/users` | Admin | List users with pagination/filtering. |
| `POST` | `/admin/users` | Admin | Create user and matching role profile. |
| `PUT` | `/admin/users/{userId}` | Admin | Update approved account fields/role; apply role profile mapping transactionally. |
| `POST` | `/admin/users/{userId}/disable` | Admin | Disable account. |
| `POST` | `/admin/users/{userId}/enable` | Admin | Enable account. |
| `GET` | `/admin/reports/participation` | Admin; Faculty read-only if approved by team | Basic participation/feedback summary. |

Feedback request: `{ "rating": 5, "comments": "Well organized." }`. Server sets `studentId` and `submittedAt`; client must not send them.

### Participation report response

`GET /api/admin/reports/participation` (Admin only) returns status aggregates plus a per-event participation table ordered by confirmed seats (ties by `id` ascending, maximum 50 rows):

```json
{
  "events": { "total": 4, "pendingApproval": 1, "approved": 2, "rejected": 0, "cancelled": 1 },
  "registrations": { "total": 10, "confirmed": 7, "cancelled": 3 },
  "feedback": { "count": 4, "averageRating": 4.25 },
  "participation": [
    {
      "eventId": 5,
      "title": "Tech Fest Workshop",
      "status": "APPROVED",
      "capacity": 50,
      "confirmedCount": 12,
      "cancelledCount": 2,
      "feedbackCount": 3,
      "averageRating": 4.33
    }
  ]
}
```

`averageRating` is `null` wherever no feedback exists yet (summary and rows). No student identities are exposed. Faculty access to this endpoint is deferred until the team approves the "Faculty read-only" option noted in the table.

## Admin user management

The `/api/admin/users` endpoints listed in the Feedback and reports table require the `ADMIN` role. All failures use the shared error body and codes from "Common conventions". Password hashes and internal authentication details are never returned.

### User representation

```json
{
  "id": 43,
  "fullName": "Dr. Meera Rao",
  "email": "meera.rao@example.edu",
  "department": "Computer Science",
  "role": "FACULTY",
  "enabled": true,
  "profile": {
    "employeeId": "E1001",
    "designation": "Professor"
  }
}
```

`profile` is a nested object whose fields depend on `role`:

- `STUDENT`: `rollNumber` (maximum 40 characters), `semester` (minimum 1), `batch` (maximum 40 characters).
- `ORGANIZER`: `designation` (maximum 100 characters).
- `FACULTY`: `employeeId` (maximum 40 characters), `designation` (maximum 100 characters).
- `ADMIN`: `adminLevel` (minimum 1; defaults to 1 when omitted).

`fullName` is required and has a maximum of 120 characters. `email` is required, must be a valid email address, and has a maximum of 254 characters. `department` is required and has a maximum of 120 characters.

### List users

`GET /api/admin/users`

Optional query parameters:

- `page`: zero-based page index; default `0`; minimum `0`.
- `size`: page size; default `20`; minimum `1`; maximum `100`.
- `role`: one of `STUDENT`, `ORGANIZER`, `FACULTY`, `ADMIN`.
- `enabled`: `true` or `false`.
- `q`: case-insensitive substring search on `fullName` or `email`.

Results are ordered by `id` ascending. A successful request returns `200 OK`:

```json
{
  "items": [
    {
      "id": 41,
      "fullName": "Ayesha Khan",
      "email": "ayesha.khan@example.edu",
      "department": "Computer Science",
      "role": "STUDENT",
      "enabled": true,
      "profile": {
        "rollNumber": "R2026001",
        "semester": 3,
        "batch": "2026"
      }
    }
  ],
  "page": 0,
  "size": 20,
  "totalItems": 1,
  "totalPages": 1
}
```

`totalPages` is `0` when `totalItems` is `0`. Invalid query parameters return `400 INVALID_REQUEST`.

### Create user

`POST /api/admin/users`

Request:

```json
{
  "fullName": "Ayesha Khan",
  "email": "ayesha.khan@example.edu",
  "password": "initial-password",
  "department": "Computer Science",
  "role": "STUDENT",
  "profile": {
    "rollNumber": "R2026001",
    "semester": 3,
    "batch": "2026"
  }
}
```

- `password` is required and must be 8–72 UTF-8 bytes. It is stored only as a BCrypt hash and is never returned.
- New accounts are always enabled. `enabled` is not an accepted request property; a create request must not override it.
- The `profile` object must contain only fields defined for `role`; fields required for the role must be present.
- Unsupported JSON properties in a create request are rejected with `400 INVALID_REQUEST`.
- A create request with a duplicate email or duplicate profile unique value returns `409 CONFLICT`.

A successful request returns `201 Created` with the user representation.

### Update user

`PUT /api/admin/users/{userId}`

The request is a full replacement of the editable account and profile fields:

```json
{
  "fullName": "Ayesha Khan",
  "email": "ayesha.khan@example.edu",
  "department": "Computer Science",
  "role": "ORGANIZER",
  "profile": {
    "designation": "Club Coordinator"
  }
}
```

- The editable fields are `fullName`, `email`, `department`, `role`, and `profile`.
- `password` and `enabled` are not accepted. The existing password hash is preserved.
- Unsupported JSON properties in an update request are rejected with `400 INVALID_REQUEST`.
- The profile must contain only fields defined for the new `role`; fields required for the role must be present.
- On a role change, the old role profile is removed and the new role profile is inserted in one transaction. When the role is unchanged, the existing profile is updated in place. Any failure rolls back the user and profile writes.
- If the user is currently `STUDENT` and has any row in `registrations`, `club_members`, or `feedback`, a role change away from `STUDENT` is rejected with `409 CONFLICT`. This includes cancelled registrations and left memberships. No registration, membership, or feedback row is deleted or modified.
- An administrator cannot change their own role away from `ADMIN`.
- The last enabled administrator cannot be demoted.
- A duplicate email or duplicate profile unique value returns `409 CONFLICT`.

A successful request returns `200 OK` with the updated user representation.

### Enable and disable

`POST /api/admin/users/{userId}/disable`
`POST /api/admin/users/{userId}/enable`

A successful request returns `200 OK` with the updated user representation.

- An administrator cannot disable their own account.
- The last enabled administrator cannot be disabled.
- The guardrail check and the account update occur in one transaction and are safe under concurrent administrator changes, including concurrent role changes.
- Disabling an account takes effect for subsequent authenticated requests.

### Admin user-management errors

| Status | Code | Cases |
|---|---|---|
| `400 Bad Request` | `INVALID_REQUEST` | Invalid body, invalid query parameter, unsupported JSON property, profile/role mismatch, or password outside 8–72 UTF-8 bytes. |
| `401 Unauthorized` | `UNAUTHENTICATED` | Missing, malformed, expired, or invalid token. |
| `403 Forbidden` | `FORBIDDEN` | Authenticated user is not an administrator. |
| `404 Not Found` | `NOT_FOUND` | `userId` does not identify an existing user. |
| `409 Conflict` | `CONFLICT` | Duplicate email or profile unique value; self-disable; self-demotion; disabling or demoting the last enabled administrator; role change away from `STUDENT` with existing student history. |

No user `DELETE` endpoint exists. No password-management endpoint is introduced by this contract. Password changes are outside general user updates.

## DTO boundaries

- Never serialize password hash, secret, full JWT claims or internal database exceptions.
- Request DTOs must not accept server-controlled fields such as `role` (except Admin user management), `createdBy`, event `status`, `registeredCount`, `reviewedBy`, `ticketNo`, timestamps or user IDs derived from authentication.
- Use pagination for potentially long user/event/roster lists before presentation if time allows; if paging is not implemented in MVP, set a sensible maximum result size and document it.
