# ERD Summary

```mermaid
erDiagram
    USERS ||--o| STUDENT_PROFILES : has
    USERS ||--o| ORGANIZER_PROFILES : has
    USERS ||--o| FACULTY_PROFILES : has
    USERS ||--o| ADMIN_PROFILES : has
    USERS ||--o{ EVENTS : creates
    USERS o|--o{ EVENTS : reviews
    STUDENT_PROFILES ||--o{ REGISTRATIONS : makes
    EVENTS ||--o{ REGISTRATIONS : receives
    CLUBS ||--o{ CLUB_MEMBERS : includes
    STUDENT_PROFILES ||--o{ CLUB_MEMBERS : joins
    USERS ||--o{ ANNOUNCEMENTS : publishes
    ANNOUNCEMENTS ||--o{ NOTIFICATIONS : generates
    USERS ||--o{ NOTIFICATIONS : receives
    STUDENT_PROFILES ||--o{ FEEDBACK : submits
    EVENTS ||--o{ FEEDBACK : receives
```

`users` owns common identity and role. Service-layer invariant: exactly one corresponding profile row for each enabled role type. A user's account should be disabled, not deleted, to preserve registrations/feedback/history.
