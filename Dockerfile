# ---- Stage 1: Build the JAR with Gradle ----
FROM gradle:8.10-jdk21 AS builder

WORKDIR /home/gradle/project

# Copy only Gradle configuration first for better caching
COPY server/build.gradle settings.gradle ./
COPY gradle ./gradle

# Download dependencies (cached)
RUN gradle build -x test --no-daemon || return 0

# Copy the rest of the project
COPY . .

# Build only the server module JAR
RUN gradle :server:bootJar --no-daemon

# ---- Stage 2: Runtime image ----
FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL authors="josefcernik"

WORKDIR /app

# Copy built JAR from the previous stage
COPY --from=builder /home/gradle/project/server/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]