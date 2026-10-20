# Four-Week Development Timeline

## Week 1 — lock contracts and scaffold

- Member 1: approve root package, Gradle Wrapper, backend/client module layout, error format, shared DTO names and change process.
- Member 7: schema review, migration, constraints and demo seed users/data.
- Member 2: security baseline and login contract.
- Member 8: Swing frame/navigation skeleton plus API client against mock responses.
- All members: review assigned handoff; agree business rules and feature acceptance criteria.

**Exit:** one repository builds; schema creates successfully; all shared contracts are versioned.

## Week 2 — feature implementation

- Member 2 auth/roles; Member 3 events/approval; Member 4 registration transactions; Member 5 clubs/memberships; Member 6 announcements/notifications/feedback.
- Member 7 reviews all persistence SQL/migrations and verifies constraints.
- Member 8 implements role dashboards and feature screens against existing API DTOs.
- Member 9 creates unit/API test cases as features arrive.

**Exit:** each module works with API tests, even if visual polish is minimal.

## Week 3 — connect and test workflows

- Connect Swing screens to backend endpoint-by-endpoint.
- Integrate event approval, registration and slot count as one workflow.
- Add role/ownership checks, notification recipient behavior and error dialog mappings.
- Run integration and concurrency tests; resolve issues through module owners.

**Exit:** every core role workflow is end-to-end demonstrable.

## Week 4 — hardening and presentation

- Regression testing and bug fixes.
- UI consistency, loading/error states and navigation improvements.
- Verify demo data, install/setup instructions, UML updates and OOP principle explanation.
- Prepare slides and demo script.

**Exit:** MVP passes acceptance criteria. Add stretch functionality only if all critical tests pass early.
