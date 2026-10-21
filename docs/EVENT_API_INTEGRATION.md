# Event API — Frontend Integration Guide

Practical guide for Member 8 (Swing client) to integrate the Event Management endpoints.
Contract source of truth: `docs/API_CONTRACT.md`. This document adds wire-level details,
headers, and copy-paste examples.

---

## 1. How the client talks to the server

This project is a **client–server** application:

```text
┌─────────────────────┐   HTTP + JSON    ┌──────────────────────┐   JDBC/SQL   ┌────────┐
│  Java Swing client  │ ───────────────► │  Spring Boot server  │ ───────────► │ MySQL  │
│  (user's machine)   │  ◄───────────────│  (runs on port 8080) │  ◄───────────│  8.4   │
└─────────────────────┘   JSON in/out    └──────────────────────┘              └────────┘
```

- The Swing app is the **only** client. It never connects to MySQL directly.
- The Spring Boot REST API is the **only** component that touches the database.
- Every request/response is JSON over HTTP `localhost:8080` (dev).

### Base settings for your API client

| Setting | Value |
|---|---|
| Base URL (dev) | `http://localhost:8080` |
| Content type | `application/json; charset=UTF-8` |
| Timeouts (recommended) | connect 5 s, read 10 s |
| Date/time format | `yyyy-MM-dd'T'HH:mm:ss` (campus-local, Asia/Kolkata, no timezone suffix) |
| Character encoding | UTF-8 |

### Authentication header

Every endpoint except `POST /api/auth/login` and `GET /api/health` requires the JWT
issued at login:

```http
Authorization: Bearer <accessToken-from-login-response>
```

Your `SessionContext` keeps the token in memory only (never write it to disk).
On logout, token expiry, or `401` responses: clear the token, clear cached user
data, and return to the login screen.

> **Status note:** Member 2's login/JWT filter is the auth source of truth. Until it
> is merged, the backend's bootstrap security config denies unfinished routes; your
> client code should still be written against the final contract above.

### Standard error body (all endpoints)

```json
{
  "timestamp": "2026-10-18T14:00:00Z",
  "status": 409,
  "code": "VENUE_CONFLICT",
  "message": "Venue \"Auditorium A\" is already booked from ... to ... by event \"...\".",
  "path": "/api/events"
}
```

Branch on the stable `code` field for user-facing messages; never show stack traces
or raw JSON to the user.

---

## 2. Event endpoints (implemented — Member 3)

All paths are relative to `/api`. "Who" column = which Swing role screen calls it.

| Method | Path | Who | Purpose |
|---|---|---|---|
| `GET` | `/events` | All roles | List events (server scopes by role) |
| `GET` | `/events/{eventId}` | All roles | One event's details |
| `POST` | `/events` | Organizer | Create proposal (always `PENDING_APPROVAL`) |
| `PUT` | `/events/{eventId}` | Owner Organizer | Edit own pending/rejected proposal |
| `POST` | `/events/{eventId}/approve` | Faculty/Admin | Approve pending event |
| `POST` | `/events/{eventId}/reject` | Faculty/Admin | Reject pending event (reason required) |
| `POST` | `/events/{eventId}/cancel` | Owner Organizer/Admin | Cancel pending/approved event |

### 2.1 Create a proposal — `POST /api/events`

Request:

```http
POST /api/events HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json
```

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

Response `201 Created`:

```json
{
  "id": 12,
  "title": "Open Source Day",
  "description": "Talks and workshops",
  "venue": "Auditorium A",
  "startTime": "2026-11-12T10:00:00",
  "endTime": "2026-11-12T16:00:00",
  "capacity": 120,
  "status": "PENDING_APPROVAL",
  "createdBy": 41,
  "reviewedBy": null,
  "reviewReason": null,
  "createdAt": "2026-10-18T14:00:00",
  "updatedAt": "2026-10-18T14:00:00",
  "registeredCount": 0,
  "availableSlots": 120
}
```

Never send `id`, `status`, `createdBy`, `reviewedBy`, `reviewReason`, timestamps,
`registeredCount` or `availableSlots` — the server ignores/rejects them.

**Errors:** `400 INVALID_REQUEST` (blank title/venue, end ≤ start, capacity ≤ 0),
`403 FORBIDDEN` (not an organizer), `409 VENUE_CONFLICT` (same normalized venue +
overlapping time as another pending/approved event).

### 2.2 Edit a proposal — `PUT /api/events/{eventId}`

Same body as create. Allowed only for the **owner organizer** while the event is
`PENDING_APPROVAL` or `REJECTED`. The response returns `status: "PENDING_APPROVAL"`
with `reviewedBy`/`reviewReason` cleared — i.e., the edit **resubmits** the proposal.

**Errors:** `403 FORBIDDEN` (not the owner), `409 EVENT_NOT_EDITABLE` (approved or
already cancelled — cancel and create a new proposal instead), `409 VENUE_CONFLICT`,
`409 CAPACITY_BELOW_CONFIRMED` (new capacity below current confirmed registrations).

### 2.3 List events — `GET /api/events`

Query parameters (all optional):

| Param | Example | Meaning |
|---|---|---|
| `q` | `q=workshop` | Case-insensitive match on title or venue |
| `from` | `from=2026-11-01T00:00:00` | Events starting at/after this time |
| `to` | `to=2026-12-01T00:00:00` | Events starting at/before this time |
| `status` | `status=PENDING_APPROVAL` | Exact status filter (not applied for students) |

What each role receives **without any filters**:

| Role | Sees |
|---|---|
| STUDENT | Only `APPROVED` events |
| ORGANIZER | Own proposals (any status) + all `APPROVED` events |
| FACULTY / ADMIN | Every event, every status |

Response `200 OK`: JSON array of event objects (same shape as 2.1, without `null`
optional fields — note `reviewedBy`/`reviewReason` are omitted when null).

### 2.4 Get one event — `GET /api/events/{eventId}`

Response `200 OK`: single event object. `404 EVENT_NOT_FOUND` if the event does not
exist **or** the caller's role may not see it (e.g., student requesting a pending event).

### 2.5 Approve — `POST /api/events/{eventId}/approve`

No request body. Faculty/Admin only. Server **re-checks the venue clash** at approval
time (a conflicting event may have appeared after submission).

Response `200 OK`: event with `status: "APPROVED"` and `reviewedBy` set to the reviewer's user id.

**Errors:** `403 FORBIDDEN` (organizer/student), `409 INVALID_EVENT_STATE` (not pending),
`409 VENUE_CONFLICT` (clash found at approval time), `404 EVENT_NOT_FOUND`.

### 2.6 Reject — `POST /api/events/{eventId}/reject`

```json
{ "reason": "Venue unavailable during that slot" }
```

Reason is mandatory (`400 INVALID_REQUEST` if blank). Response `200 OK`: event with
`status: "REJECTED"`, `reviewedBy` set, `reviewReason` set.

### 2.7 Cancel — `POST /api/events/{eventId}/cancel`

No request body. Owner organizer or Admin. Cancels a `PENDING_APPROVAL` or `APPROVED`
event. In ONE database transaction the backend: sets the event `CANCELLED`, creates an
`EVENT_CANCELLED` notification for every confirmed registrant, and cancels all their
registrations (seats released).

Response `200 OK`: cancelled event with `registeredCount: 0`.

**Errors:** `403 FORBIDDEN` (other organizers, faculty), `409 INVALID_EVENT_STATE`
(rejected or already cancelled), `404 EVENT_NOT_FOUND`.

### 2.8 Response field cheat-sheet

| Field | Type | Notes |
|---|---|---|
| `id` | number | Server-assigned |
| `status` | string | `PENDING_APPROVAL` \| `APPROVED` \| `REJECTED` \| `CANCELLED` |
| `createdBy` | number | Organizer user id |
| `reviewedBy` | number \| null | Faculty/Admin user id after approve/reject |
| `reviewReason` | string \| null | Present after reject |
| `registeredCount` | number | Live count of CONFIRMED registrations — never trust a stored value |
| `availableSlots` | number | `capacity - registeredCount` |

---

## 3. Registration endpoints (implemented — Member 4)

These complete the event workflow from the client's perspective.

| Method | Path | Who | Purpose |
|---|---|---|---|
| `POST` | `/events/{eventId}/registrations` | Student | Register for an approved, open event |
| `DELETE` | `/events/{eventId}/registrations/me` | Student | Cancel own registration (before start) |
| `GET` | `/registrations/me` | Student | Own registration history |
| `GET` | `/events/{eventId}/registrations` | Owner Organizer, Faculty/Admin | Roster (minimal student data) |

Register response `201`:

```json
{
  "registrationId": 91,
  "eventId": 12,
  "status": "CONFIRMED",
  "ticketNo": "EVT-12-9f8e...",
  "registeredAt": "2026-10-18T14:00:00",
  "registeredCount": 1,
  "availableSlots": 119
}
```

Registration error codes: `EVENT_NOT_FOUND`, `EVENT_NOT_APPROVED`, `REGISTRATION_CLOSED`,
`EVENT_FULL`, `DUPLICATE_REGISTRATION`, `REGISTRATION_NOT_FOUND`,
`REGISTRATION_ALREADY_CANCELLED` (all `409` except `EVENT_NOT_FOUND` = `404`).

---

## 4. Suggested Swing integration flow

1. **Login screen** → `POST /auth/login` → store token + user in `SessionContext`.
2. **Role dashboard** → `GET /events` (role-scoped automatically).
3. **Organizer**: create form → `POST /events`; proposal list = filter `status` on own events;
   edit → `PUT /events/{id}`; cancel → `POST /events/{id}/cancel`.
4. **Faculty/Admin**: review queue = `GET /events?status=PENDING_APPROVAL`;
   approve button → `POST .../approve`; reject dialog (reason text field) → `POST .../reject`.
5. **Student**: browse `GET /events`; details → `GET /events/{id}`;
   register → `POST .../registrations`; my registrations → `GET /registrations/me`;
   cancel → `DELETE .../registrations/me`.
6. Update seat counts from `registeredCount`/`availableSlots` on every event response —
   they are live query results.

### UI rules that mirror backend rules (convenience only — backend is authoritative)

- Students never see a "Create Event" button.
- Only the event's creator sees Edit/Cancel; Admin additionally sees Cancel.
- Only Faculty/Admin see Approve/Reject, and only while `status == PENDING_APPROVAL`.
- Registered count is always re-fetched; never increment/decrement locally as source of truth.

---

## 5. Running the stack for integration testing

```bash
# one-time: create DB + app user (see README Quick start)
mysql -u root -p < database/create_database.sql

# terminal 1 — server
export DB_URL="jdbc:mysql://localhost:3306/student_portal?serverTimezone=Asia/Kolkata"
export DB_USERNAME="student_portal_app"
export DB_PASSWORD="<your-local-password>"
export JWT_SECRET="<local-random-secret-at-least-32-bytes>"
./gradlew :backend:bootRun

# terminal 2 — Swing client
./gradlew :client:run
```

Smoke-check the server: `curl http://localhost:8080/api/health`.
