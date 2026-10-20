# Definition of Done

A task is done only when all applicable points below are true:

- [ ] Behavior matches the project spec and assigned handoff.
- [ ] Existing shared API/schema contracts are unchanged unless approved.
- [ ] No direct Swing-to-MySQL connection exists.
- [ ] Backend SQL is parameterized; no JPA/Spring Data was introduced.
- [ ] Role and ownership checks are on the backend.
- [ ] Expected failures produce the agreed API error code/message/status.
- [ ] Unit tests cover normal and failure paths.
- [ ] Transaction/concurrency behavior is covered where relevant.
- [ ] Code compiles using the committed Gradle Wrapper.
- [ ] No secrets, local paths or unrelated files were committed.
- [ ] PR description explains changes, tests and integration notes.
- [ ] Member 9 has enough information to add/execute acceptance tests.
