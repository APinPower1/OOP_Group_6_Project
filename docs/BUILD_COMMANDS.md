# Build Commands

Run commands from the repository root after Member 1 has generated and committed Gradle Wrapper 8.14.3.

Windows:
```powershell
.\gradlew.bat clean build
.\gradlew.bat :backend:bootRun
.\gradlew.bat :client:run
```

macOS/Linux:
```bash
./gradlew clean build
./gradlew :backend:bootRun
./gradlew :client:run
```

The backend and Swing client are separate modules. Run the backend first, then the client. Ensure local MySQL and the backend environment variables are configured before testing database endpoints. The bootstrap security config deliberately allows only `GET /api/health`; all other API routes remain denied until Member 2 implements JWT.

To generate the Wrapper once before it exists:
```bash
gradle wrapper --gradle-version=8.14.3
```
Do not commit a locally installed Gradle directory or downloaded binary distribution. Commit only the normal Gradle Wrapper files.
