# Acceptance Report — Real Chain (Spring Security + JWT)

Date: 2026-10-22
Suite: `backend/src/test/java/edu/mec/studentportal/acceptance/`
Result: **21 tests, 0 failures, 0 skipped** (full build: **590 tests, 0 failures, 0 skipped**)

## What makes these tests different

These are `@SpringBootTest` + `MockMvc` tests that exercise the **production security
chain** (`BootstrapSecurityConfig` + `JwtAuthenticationFilter`) with real passwords
(BCrypt) and real JWTs from `POST /api/auth/login`. They deliberately do **not** import
`TestAuthSecurityConfig`, whose `X-Test-Auth` header bypass replaces the security filter
chain with `permitAll`. Every token used here was issued by the real login endpoint and
is verified by the real filter on every call.

Database: local MySQL test schema via `TestDatabaseAvailability` (skips silently when
unavailable). Tables are truncated in `@BeforeEach`. Times use `Asia/Kolkata`, truncated
to seconds to match stored `DATETIME` precision.

Run:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 TEST_DB_PASSWORD=local-dev-password \
  ./gradlew :backend:test --tests 'edu.mec.studentportal.acceptance.*'
```

## Defects found and fixed by this suite

| # | Severity | Symptom | Root cause | Fix |
|---|----------|---------|------------|-----|
| 1 | **P0** | Every `/api/events` endpoint returned `401 UNAUTHENTICATED` for a valid JWT | `EventController` resolved the user with its own `Long.parseLong(authentication.getName())`. The real chain sets a `UserSecurityState` principal whose `getName()` is not numeric, so parsing always threw. Invisible before because `TestAuthSecurityConfig` sets the principal to a numeric string. | Replaced the private helper with the shared `CurrentUser.id(authentication)` used by every other controller (`EventController.java`) |
| 2 | Low | `POST /api/clubs/{id}/membership` executed the same UPDATE twice | Accidental duplicate `dao.reactivateMembership(...)` line in `ClubService.join` | Removed the duplicate line |

## Scenario → test evidence

### Authentication and authorization — `AuthenticationAuthorizationAcceptanceTest` (8)

| Scenario (TEST_PLAN) | Test method | Evidence |
|---|---|---|
| Correct login returns token and user summary (all 4 roles) | `allFourRolesAuthenticate` | 200 + non-blank JWT + role echo for STUDENT/ORGANIZER/FACULTY/ADMIN |
| Incorrect credentials return generic 401 | `invalidCredentialsReturnGeneric401` | 401 `UNAUTHENTICATED`, body never says which account exists |
| Anonymous call to protected route is unauthenticated | `anonymousCallToProtectedRouteIsUnauthenticated` | 401 via security entry point |
| Admin-only actions reject other roles | `studentAndOrganizerAreForbiddenFromAdminRoutes` | 403 for GET+POST `/api/admin/users` |
| Role authority enforced server-side, not just hidden buttons | `onlyOrganizersMayCreateEvents`, `studentCannotPublishAnnouncements` | Student `POST /api/events` → 403, student `POST /api/announcements` → 403 |
| Disabled users cannot continue authorized requests | `disabledUserIsRejectedOnNextRequestAndCannotLogIn` | Valid token → 401 after disable; login → 401; re-enable restores both |
| Auth identity echo | `authMeEchoesAuthenticatedUser` | `/api/auth/me` matches the logged-in user |

### Event lifecycle — `EventLifecycleAcceptanceTest` (5)

| Scenario | Test method | Evidence |
|---|---|---|
| Proposal hidden from students until faculty approves | `proposalBecomesVisibleToStudentsOnlyAfterFacultyApproval` | Student list: 0 before, 1 after faculty `POST /{id}/approve` (201 start → visibility flip) |
| Approve/reject only Faculty/Admin; non-owner cannot edit | `nonFacultyCannotModerateAndNonOwnerCannotEdit` | Organizer approve → 403, other organizer reject → 403, non-owner `PUT` → 403 |
| Reject requires reason and stores it | `rejectRequiresReasonAndStoresItForTheOrganizer` | Blank reason → 400; stored reason visible to owner; status `REJECTED` |
| Overlap refused at creation | `overlappingProposalIsRefusedAtCreation` | Case-variant venue overlap → 409 `VENUE_CONFLICT`; non-overlapping → 201 |
| Overlap rechecked at approval | `overlapIsRecheckedWhenFacultyApproves` | Competing pending row created, first event's time shifted via SQL so rows now overlap, approve → 409 `VENUE_CONFLICT` |

### Registration and feedback — `RegistrationAndFeedbackAcceptanceTest` (4)

| Scenario | Test method | Evidence |
|---|---|---|
| Register, duplicate, cancel, re-register seat math | `registerDuplicateCancelAndReactivateConsumesAndReleasesSeats` | `/api/registrations/me` capacity via helper: 10→9→duplicate 409 `DUPLICATE_REGISTRATION`→cancel 200→10→re-register 9 |
| Full event refuses registration | `fullEventRefusesFurtherRegistrations` | Capacity 1 event: second student → 409 `EVENT_FULL` |
| Pending event refuses registration | `pendingEventRefusesRegistration` | 409 `EVENT_NOT_APPROVED` |
| Feedback gating, dedupe, range, summary privacy | `feedbackRequiresEndedEventConfirmedSeatAndSingleSubmission` | Before end → 409 `FEEDBACK_NOT_ALLOWED` (unregistered and registered students); after SQL time-shift: no-registration → 409, valid submit → 201, duplicate → 409 `DUPLICATE_FEEDBACK`, rating 6 → 400; summary `{ratingCount:1, averageRating:5.0}` with no student identity |

### Admin, reports, announcements, clubs — `AdminUsersReportsAnnouncementsAcceptanceTest` (4)

| Scenario | Test method | Evidence |
|---|---|---|
| Admin manages users through real API | `adminCreatesFiltersAndDisablesUsersThroughTheRealApi` | Create user with nested profile → 201; role filters; disable revokes live token + login; enable restores |
| Participation report is Admin-only and real | `participationReportIsAdminOnlyAndReflectsRealApiData` | Organizer/student → 403; admin → 200; aggregates equal data seeded through the API; payload contains no student email |
| Announcement audience filtering + notification isolation | `announcementCreatesAudienceScopedNotificationsThatStayPrivate` | Publish → 201; student sees it; non-target organizer sees 0 notifications; foreign `markRead` → 403; own `markRead` → 200 with `readAt` set |
| Club management and membership rules | `adminManagesClubsAndStudentsControlOnlyTheirMembership` | Admin create → 201, student create → 403; join → 201; **duplicate join → 201 idempotent, roster still exactly 1 row** (matches TEST_PLAN "no duplicate row"); roster shows member by `rollNumber`; leave → 200; deactivate → 200; join after deactivate → 409 `CLUB_INACTIVE` |

## Behavioral notes (confirmed, not defects)

- Event creation returns **201 Created**, feedback submission returns **201 Created**.
- Duplicate club join is **idempotent**: returns 201 with the current membership and never
  creates a second row (documented behavior in `ClubService.join`).
- Feedback summary field for the size is `ratingCount` (not `count`).
- Club roster rows expose `rollNumber`, not email (privacy-shaped DTO).

## Coverage limits

- Concurrency/overbooking races stay in the dedicated registration concurrency tests
  (not re-duplicated here).
- Swing UI driving is out of scope for these tests and is covered by the 39 client tests
  plus the manual demo walkthrough in the final report.
- Tests require the local MySQL test schema; they skip silently without it (CI provides it).
