# Architecture

## High-level architecture

```text
┌───────────────────────────────────────┐
│ Java Swing Desktop Client              │
│ Views (JFrame/JPanel/JTable)           │
│ Client controllers + session state     │
│ Shared HTTP API client                 │
│ In-memory JWT for current session      │
└──────────────────┬────────────────────┘
                   │ HTTPS in deployment / HTTP localhost in development
                   │ JSON + Authorization: Bearer JWT
┌──────────────────▼────────────────────┐
│ Spring Boot Backend                   │
│ Spring Security + JWT filter           │
│ REST Controllers                      │
│ Services / business rules              │
│ JDBC DAOs using JdbcTemplate            │
│ Transaction manager / DataSource pool  │
└──────────────────┬────────────────────┘
                   │ JDBC (backend only)
┌──────────────────▼────────────────────┐
│ MySQL / InnoDB                         │
│ users + role profiles, events,          │
│ registrations, clubs, memberships,     │
│ announcements, notifications, feedback │
└───────────────────────────────────────┘
```

## Why the UML architecture was updated

The original UML put Swing, JDBC and MySQL in one application tier. The chosen stack adds Spring Boot and JWT, so the clean implementation is a client/server split. MVC remains valid: it applies in Swing, while the backend also has Controller/Service/DAO layers. The desktop never receives database credentials or the JWT signing secret.

## Backend package convention

Root package: `edu.mec.studentportal` (approved default; Member 1 may change before coding, never member-by-member).

```text
backend/src/main/java/edu/mec/studentportal/
├── StudentPortalApplication.java
├── config/               # CORS if needed, Jackson/time configuration, app security
├── security/              # JWT encoder/decoder/filter, user details, auth rules
├── common/                # ApiError, domain exceptions, validation helpers
├── auth/                  # AuthController, AuthService, login DTOs
├── user/                  # User, role profiles, UserController/Service/DAO/DTOs
├── event/                 # EventController/Service/DAO/DTOs, EventStatus
├── registration/          # RegistrationController/Service/DAO/DTOs
├── club/                  # ClubController/Service/DAO/DTOs
├── announcement/          # AnnouncementController/Service/DAO/DTOs
├── notification/          # NotificationController/Service/DAO/DTOs
├── feedback/              # FeedbackController/Service/DAO/DTOs
└── report/                # ReportController/Service/DAO/DTOs
```

The bootstrap `BootstrapSecurityConfig` permits only `GET /api/health` and denies all other requests until Member 2 implements JWT security. Do not change it to `permitAll` as a shortcut.

No module should import another module's DAO directly. Cross-module behavior goes through a service or a clearly documented query/service interface. Keep modules package-private where useful; prefer constructor injection.

## Swing client layout

```text
client/src/main/java/edu/mec/studentportal/client/
├── ClientApplication.java
├── config/                # ApiConfig, timeout/base URL settings
├── api/                   # ApiClient, ApiException, DTO transport types
├── auth/                  # SessionContext, LoginController, LoginView
├── common/                # navigation, dialogs, reusable Swing components
├── student/               # StudentDashboard, event/club/feedback screens
├── organizer/             # OrganizerDashboard, event composer, roster
├── faculty/               # FacultyDashboard, approval queue
└── admin/                 # AdminDashboard, users/clubs/reports
```

Swing UI modifications must run on the Event Dispatch Thread. Do not block it with network calls; use `SwingWorker` or another shared background-call helper. One API client implementation handles base URL, JWT header, JSON conversion, timeouts and common error parsing.

## Backend request flow

1. Swing validates basic form fields and calls the shared API client.
2. Spring Security verifies the JWT (except login/health endpoints) and establishes the current user/authorities.
3. REST Controller validates the request DTO and calls a Service.
4. Service checks business rules, ownership and role-sensitive conditions.
5. DAO executes parameterized SQL via `JdbcTemplate`.
6. Transaction manager commits or rolls back as appropriate.
7. Controller returns a defined response DTO or standardized error response.

## Transaction ownership

Transactions belong in Service methods, not Swing controllers and not scattered across individual DAO calls. `@Transactional` must encompass every DAO operation participating in a registration/cancellation/approval transaction. The event row lock and query must use the same transaction/connection.
