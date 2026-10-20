# Git Workflow

## Branches

- `main`: always builds and is demoable.
- `develop`: integration branch if the team chooses to use one; otherwise use pull requests directly into `main` during a small project.
- Feature branches: `feature/auth-jwt`, `feature/events`, `feature/registration`, `feature/clubs`, `feature/announcements-feedback`, `feature/swing-client`, `feature/database-migrations`, `test/e2e`.

Member 1 decides whether `develop` is needed and records the decision before the first shared commit. Do not create competing conventions.

## Before coding

1. Pull latest base branch.
2. Read the shared contracts and your member handoff.
3. Ask your AI to list files it intends to create/modify before generating code.
4. Confirm those files are in your ownership boundary.
5. Implement against existing shared DTOs/statuses/endpoints.

## Pull request checklist

GitHub loads .github/PULL_REQUEST_TEMPLATE.md automatically for new pull requests. The template requires submitters to mark whether shared contracts changed and link the approved proposal; follow docs/SHARED_CONTRACT_CHANGE.md before changing a shared contract.

- [ ] Purpose and member/module are stated.
- [ ] No unapproved API/schema/enum/dependency changes.
- [ ] No secrets or local config committed.
- [ ] Unit tests added/updated and passing.
- [ ] Cross-module compile/test passes where relevant.
- [ ] API responses and error codes match the contract.
- [ ] README/docs updated if behavior or setup changed.
- [ ] No unrelated formatting/restructure in another member's files.

## Merge order recommendation

1. Shared build/project scaffold and security/API error skeleton (Member 1).
2. Database schema/migrations and configuration (Member 7).
3. Auth/security (Member 2).
4. Event core (Member 3) and registration transaction (Member 4), developed in parallel after DTO/schema signatures are agreed.
5. Clubs (Member 5), announcements/feedback/notifications (Member 6).
6. Swing UI/API client (Member 8) can mock against the contract early, then connect to backend.
7. Member 9 integrates tests throughout; final full regression is not left until the last day.
