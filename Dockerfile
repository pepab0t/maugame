# ---- Builder stage ----
FROM jelastic/maven:3.9.5-openjdk-21 AS builder

WORKDIR /builddir

# Copy and install the first module
COPY maugame.engine maugame.engine
RUN mvn -f maugame.engine/pom.xml clean install

# Copy and build the second module
COPY maugame.websocket maugame.websocket

# Make sure wrapper is executable if you're using it
RUN chmod +x maugame.websocket/mvnw

# You can use the wrapper, or the system mvn (since you’re on a Maven image)
# RUN maugame.websocket/mvnw -f maugame.websocket/pom.xml clean package
# OR (simpler)
RUN mvn -f maugame.websocket/pom.xml clean package

# ---- Runtime stage ----
FROM alpine/java:21-jre

WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /builddir/maugame.websocket/target/websocket-0.0.1-SNAPSHOT.jar .

EXPOSE 8080

CMD ["java", "-jar", "websocket-0.0.1-SNAPSHOT.jar"]

