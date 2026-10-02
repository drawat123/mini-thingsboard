# ---------- Stage 1: build the JAR (needs the full JDK + Maven) ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY src src

# Cache ~/.m2 between builds so dependencies aren't downloaded every time.
# Tests are skipped: they need a running Postgres, which doesn't exist during the build.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -q package -DskipTests && cp target/*.jar app.jar

# ---------- Stage 2: runtime image (only a JRE + our JAR) ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Don't run as root inside the container.
RUN useradd --system --uid 1001 minitb
USER minitb

COPY --from=build /workspace/app.jar app.jar

EXPOSE 8080

# Let the JVM use up to 75% of the container's memory limit (the default is only 25%).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
