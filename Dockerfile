# Multi-stage build for the Student Portal backend REST API.
# The Swing client is a desktop application and is intentionally not containerized:
# build it with `./gradlew :client:jar` (or run with `./gradlew :client:run`) on the host.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Build scripts first so dependency resolution is cached until they change.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY backend/build.gradle.kts ./backend/build.gradle.kts
COPY client/build.gradle.kts ./client/build.gradle.kts
# Warm the Gradle dependency cache (main + test classpaths) until build scripts change.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew \
    && mkdir -p backend/src/main/java backend/src/test/java client/src/main/java \
    && ./gradlew --no-daemon --quiet :backend:compileTestJava

# Compile (warms the Gradle cache for the real sources), then package the boot jar.
COPY backend ./backend
RUN ./gradlew --no-daemon :backend:bootJar

FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 1001 --create-home portal
WORKDIR /app
COPY --from=build /workspace/backend/build/libs/*.jar app.jar
USER portal
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
  CMD curl -fsS http://localhost:8080/api/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
