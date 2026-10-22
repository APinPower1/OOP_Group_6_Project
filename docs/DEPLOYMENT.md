# Deployment

What ships, how to run it, and how it is verified.

## Components

| Component | Artifact | Runs |
|---|---|---|
| Backend REST API | `Dockerfile` → image with `bootJar` | Container (or `./gradlew :backend:bootRun` on the host) |
| MySQL 8.4 | `mysql:8.4` image via `docker-compose.yml` | Container (or a host install per `docs/LOCAL_DEVELOPMENT.md`) |
| Swing client | `./gradlew :client:jar` (or `:client:run`) | Developer desktop — **never containerized**, it needs a display and has no DB access by design |

## Quickstart (full stack in containers)

```bash
docker compose up --build                 # MySQL + backend on http://localhost:8080
curl -fsS http://localhost:8080/api/health
DEMO_SEED=true docker compose up --build  # additionally seed the four demo accounts
```

If host port 3306 is occupied by an existing MySQL: `DB_PORT=13306 docker compose up --build`.

Then point the Swing client at it (defaults already match):

```bash
./gradlew :client:run
# or explicit config: -Dclient.config=client-config.example.properties
```

## Environment variables (backend)

| Variable | Purpose | Default |
|---|---|---|
| `DB_URL` | JDBC URL | `jdbc:mysql://localhost:3306/student_portal?serverTimezone=Asia/Kolkata` |
| `DB_USERNAME` / `DB_PASSWORD` | DB account (least privilege, see `docs/LOCAL_DEVELOPMENT.md`) | none — required |
| `JWT_SECRET` | HS256 signing secret, **min 32 bytes**, startup fails if blank/too short | none — required |
| `JWT_TTL_SECONDS` | Access-token lifetime | `1800` |
| `SERVER_PORT` | HTTP port | `8080` |
| `DEMO_SEED` | Seeds the four demo accounts; double opt-in with schema `student_portal` only (`docs/DECISION_LOG.md` D-020) | `false` |

Copy `backend/.env.example` for local values. **Never commit real secrets.**

Flyway applies `V1__initial_schema.sql` automatically on startup; there is no separate
migrate step. Migrations are forward-only.

## Continuous integration

`.github/workflows/ci.yml` runs on every push to `main` and every pull request:

1. Gradle wrapper validation.
2. JDK 21 (Temurin) with Gradle dependency caching.
3. A throwaway `mysql:8.4` service container pre-provisioned with the
   `student_portal_test` schema and a least-privilege `student_portal_test_app` account
   (exactly what `docs/TEST_DATABASE_SETUP.md` describes locally).
4. `./gradlew build` — compiles backend + client and runs the **full** suite (unit,
   integration, concurrency, the 39 client tests, and the 21 real-chain acceptance
   tests). DB-backed tests would skip silently without the service; the service makes
   them run, so a red database fails the build.
5. On failure, test reports are uploaded as artifacts.

CI credentials in the workflow are throwaway container values and never grant access to
anything that outlives the job. No repository secrets are required.

## Production checklist

- Generate a real secret: `openssl rand -base64 48` → set `JWT_SECRET`.
- Use a managed/self-hosted MySQL 8.4 with a dedicated app account limited to the
  `student_portal` schema; set `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` from a secret store.
- Keep `DEMO_SEED` unset (default) — demo accounts must not exist in production.
- Terminate TLS in front of the app (reverse proxy); the backend itself speaks HTTP.
- Restrict the DB port; it should not be reachable from the public network.
- Back up the database; schema changes arrive only via committed Flyway migrations.
- Watch `/api/health` for liveness (the container healthcheck already uses it).

## Verification commands

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 TEST_DB_PASSWORD=<pw> ./gradlew clean build   # full suite
docker compose up --build && curl -fsS http://localhost:8080/api/health              # runtime smoke
```
