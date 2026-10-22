# OOP_Group_6_Project

This pack is the shared implementation contract for the nine-person OOP project team. It is based on the updated six-page UML, with agreed corrections for architectural consistency and the technology stack supplied by the team.

## Approved architecture for this pack

- **Desktop client:** Java Swing
- **Backend:** Java + Spring Boot REST API
- **Database access:** JDBC through Spring JDBC / `JdbcTemplate`; no JPA/Hibernate/Spring Data repositories
- **Database:** MySQL
- **Build:** Gradle Wrapper
- **Authentication:** JWT, issued and validated only by the backend
- **Pinned baseline:** Java 21, Spring Boot 3.5.16, Gradle Wrapper 8.14.3, MySQL 8.4 LTS (see `docs/BUILD_BASELINE.md`)

### Important architecture correction

Because JWT and Spring Boot are part of the stack, the Swing client is **not allowed to connect directly to MySQL**. The client calls the Spring Boot API over HTTP; the backend enforces authorization and accesses MySQL through JDBC. Do not put database credentials or the JWT signing secret in the Swing client.

## Read these first

1. `HANDOFF.md`
2. `docs/BUILD_BASELINE.md`
3. `docs/PROJECT_SPEC.md`
4. `docs/STACK_AND_DECISIONS.md`
5. `docs/ARCHITECTURE.md`
6. `docs/API_CONTRACT.md`
7. `docs/DATA_MODEL.md`
8. `docs/AUTH_AND_SECURITY.md`
9. The assigned `member-handoffs/MEMBER-XX.md`
10. `docs/AI_AGENT_PROMPT.md`

All members must use the same versions of the shared contracts. If an AI proposes a change to an endpoint, DTO, class name, table, status, enum or shared configuration, stop and request approval before implementing it.

## Repository layout

```text
student-portal/
├── backend/                 # Spring Boot REST API; only this module accesses MySQL
├── client/                  # Java Swing desktop UI and REST API client
├── database/
│   ├── create_database.sql
│   └── ERD.md
├── backend/src/main/resources/db/migration/
│   └── V1__initial_schema.sql
├── docs/                    # Shared contracts and team workflow
├── diagrams/                # Updated architecture/flow sources
├── member-handoffs/         # One scoped assignment per member
├── HANDOFF.md
└── README.md
```

The ZIP includes shared specifications, a Gradle multi-project scaffold, a Flyway schema migration and minimal backend/client entry points; it is not a completed application. Member 1 must generate and commit the Gradle Wrapper once using `docs/BUILD_BASELINE.md`, then everyone uses that committed wrapper.

## Conflict-prevention rule

**Shared files have one owner. Feature code may depend on shared contracts, but must not silently redefine them.** See `docs/TEAM_OWNERSHIP.md` and `docs/SHARED_CONTRACT_CHANGE.md`.
## Quick start

### Requirements

- JDK 21
- Gradle Wrapper 8.14.3 (included; no separate Gradle install needed)
- MySQL 8.4 LTS
- Spring Boot 3.5.16 (managed by the backend Gradle plugin)

### Build

Windows PowerShell:

```powershell
.\gradlew.bat clean build
```

macOS/Linux:

```bash
./gradlew clean build
```

### Run the backend

Create the local database once with MySQL. Then create a local application account with access to that database (or use an existing least-privilege account). Flyway applies the committed schema migration on startup. Use the same local password in the commands below:

```sql
CREATE USER 'student_portal_app'@'localhost' IDENTIFIED BY 'replace-this-locally';
GRANT ALL PRIVILEGES ON student_portal.* TO 'student_portal_app'@'localhost';
```

Windows PowerShell:

```powershell
Get-Content .\database\create_database.sql | mysql -u root -p
$env:DB_URL = "jdbc:mysql://localhost:3306/student_portal?serverTimezone=Asia/Kolkata"
$env:DB_USERNAME = "student_portal_app"
$env:DB_PASSWORD = "replace-this-locally"
$env:JWT_SECRET = "your-local-random-secret-at-least-32-bytes"
.\gradlew.bat :backend:bootRun
```

macOS/Linux: run the database creation command below, create the same local MySQL account and grant shown above in the MySQL shell, then export the same `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET` values before starting the backend.

```bash
mysql -u root -p < database/create_database.sql
./gradlew :backend:bootRun
```

The health check is public at `http://localhost:8080/api/health`. Public routes are limited to health and login; every other API route requires a valid JWT and enforces role/ownership authorization. Do not put real credentials in source control; see `backend/.env.example` and `docs/LOCAL_DEVELOPMENT.md` for local configuration details.

### Run the Swing client

Start the backend first, then launch the client in another terminal:

```powershell
.\gradlew.bat :client:run
```

On macOS/Linux use `./gradlew :client:run`. The client uses the HTTP API; it does not connect directly to MySQL.

The desktop client provides role-specific dashboards: students discover and register for events and submit feedback, organizers manage proposals and rosters, faculty review proposals, and administrators manage users, moderation, clubs, announcements and reports. Network requests run outside Swing's Event Dispatch Thread, and the JWT is held in memory only. The default API URL is `http://localhost:8080/api`. To change it, copy `client/client-config.example.properties` to `client/client-config.properties`, or point the `client.config` system property at another properties file.

See `docs/BUILD_COMMANDS.md` for the full command list and `docs/BUILD_BASELINE.md` for the version ownership rules.
