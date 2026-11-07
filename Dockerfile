# ---- Stage 1: Build the JAR with Gradle ----
FROM gradle:9.2.1-jdk25 AS builder

WORKDIR /home/gradle/project

# Copy the whole project (see .dockerignore)
COPY . .

# Build only the server module JAR.
# The Gradle user home (downloaded dependencies, wrapper distributions, build cache)
# lives in a persistent BuildKit cache volume, so it survives across builds even
# when sources or build files change.
RUN --mount=type=cache,id=maugame-gradle,target=/home/gradle/.gradle,uid=1000,gid=1000 \
    gradle :server:bootJar -x test --no-daemon

# ---- Stage 2: Runtime image ----
FROM eclipse-temurin:25-jre-alpine AS runtime

LABEL authors="josefcernik"

WORKDIR /app

# Copy built JAR from the previous stage
COPY --from=builder /home/gradle/project/server/build/libs/*.jar app.jar

EXPOSE 9090

ENTRYPOINT ["java", "-jar", "/app/app.jar"]