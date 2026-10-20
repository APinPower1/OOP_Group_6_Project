# Swing Client Contract

## Responsibilities

- Display screens, collect form inputs and navigate according to role.
- Call backend API through one reusable HTTP API client.
- Keep JWT/user summary in an in-memory `SessionContext` only.
- Parse standardized API errors and show understandable messages.
- Never connect to MySQL or embed the JWT secret.

## Shared client APIs (Member 8 owns implementation)

Recommended interfaces; Member 1 approves exact names before first commit:

```java
interface ApiClient {
    <T> T get(String path, Class<T> responseType);
    <T> T post(String path, Object requestBody, Class<T> responseType);
    <T> T put(String path, Object requestBody, Class<T> responseType);
    void delete(String path);
}
```

If the team chooses not to use generic methods, keep the same central behavior: base URL, JSON handling, bearer-token injection, connection/read timeout, standardized `ApiException`, and one place for HTTP status mapping.

## Session state

`SessionContext` exposes current user summary, role, token and logged-in state. It must support `setSession`, `clearSession`, and token access for the API client. Do not save JWT to disk. If a request returns `401`, clear session and prompt login. If a request returns `403`, display access denied without silently retrying.

## Swing concurrency

- Construct/show/update components on Event Dispatch Thread.
- Network calls run off EDT (e.g. `SwingWorker`).
- Disable submit buttons during in-flight writes to avoid accidental repeated registration/submission, but rely on backend/database rules for correctness.
- Handle loading, empty, success and error states.
- No UI class contains SQL or domain authorization logic.
