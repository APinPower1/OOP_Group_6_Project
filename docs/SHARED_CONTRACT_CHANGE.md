# Shared Contract Change Process

Any change to API endpoint paths/methods, JSON DTO properties, roles, status enums, database columns/constraints, package root, dependency versions or transaction behavior is a shared contract change.

1. Proposer files an issue/PR titled `Contract proposal: <short change>`.
2. The proposal states why the current contract is insufficient, alternatives considered, affected files/modules, migration/backward-compatibility consequences and tests.
3. Owners involved: Member 1 (architecture/API), Member 7 (schema/migrations), affected feature owner(s), Member 8 (Swing DTO/client impact), Member 9 (test impact).
4. Do not merge until affected owners approve.
5. Update source contract first (`API_CONTRACT.md`, `DATA_MODEL.md`, `STACK_AND_DECISIONS.md` etc.), then code and tests in the same coordinated PR series.
6. Mark a versioned contract note in `docs/DECISION_LOG.md`.
7. All members sync before continuing AI-generated work.

An AI recommendation is not approval. Do not ask one AI agent to silently edit shared contracts as part of a feature implementation.
