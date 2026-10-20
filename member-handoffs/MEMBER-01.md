# Member 01 — Team Lead, Architecture and Build Scaffold

## Mission
Create and maintain the common project skeleton and make sure all nine members code against the same contracts. You own build versions and shared API/error conventions, not every feature implementation.

## Own these files
- Root Gradle settings/build files and Gradle Wrapper
- `backend`/`client` module scaffold, shared package root
- Common API error DTO/exception handler and health endpoint skeleton
- `docs/STACK_AND_DECISIONS.md`, shared contracts (with affected owners)

## Deliverables
1. One root multi-project build (`backend` + `client`) and committed Gradle Wrapper 8.14.3.
2. Backend application starts and exposes `GET /api/health`; bootstrap security denies unfinished endpoints by default.
3. Client launches a minimal Swing frame.
4. Shared API error structure and package conventions compile.
5. README start commands and dependency versions documented.
6. Contract-change PR process enforced.

## Do not
- Change schema without Member 7.
- Implement all feature modules yourself.
- add JPA/Hibernate/Spring Data.
- allow a Swing-to-MySQL connection.
- silently rename a shared DTO/status or adopt a new dependency.

## Definition of done
Generate the Wrapper once with `gradle wrapper --gradle-version=8.14.3`, commit the wrapper files, and verify a clean checkout can run `./gradlew build`; backend/client entry points compile and the client starts. Do not claim the API is secured until Member 2 completes JWT security.
