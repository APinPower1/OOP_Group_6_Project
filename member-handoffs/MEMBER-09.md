# Member 09 — Integration, Test Automation and Release Readiness

## Mission
Maintain acceptance tests and ensure the full app works through Swing → Spring Boot → JDBC → MySQL. Do not independently redesign modules to fix failures; create issues and work with the module owner.

## Own
- Cross-module integration/E2E tests and test fixtures
- `docs/TEST_PLAN.md` and test evidence/bug list
- Presentation smoke-test checklist and final regression report

## Start early
Create tests as soon as endpoints arrive. Don't wait until the final week. Build API tests for contracts even while Swing UI is mocked.

## Critical scenarios
- All four roles authenticate and are blocked from unauthorized actions.
- Organizer creates pending event; Faculty/Admin approves/rejects; Student sees only approved event.
- Same venue/time overlap cannot both be approved.
- Last seat race never overbooks; duplicate registration is prevented in service and DB.
- Registration cancellation releases capacity.
- Event cancellation cancels registrations and notifies students.
- Announcement audience and notification privacy work.
- Feedback eligibility and unique constraint work.
- User disable takes effect for future requests.
- Swing workflows call the real API and persist results in MySQL.

## Deliverables
- Automated test suite for high-risk flows.
- Repeatable setup/test commands.
- Issue severity list with owner and reproduction steps.
- Final acceptance checklist and evidence.
- No open critical security/data-integrity bug at sign-off.

## Do not
- Change endpoint/database contracts directly in a test branch.
- Mark a test passed without running it.
- leave integration until Week 4.
