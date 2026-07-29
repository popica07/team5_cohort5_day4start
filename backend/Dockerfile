# ============================================================================
# TICKET-ADV146 — Spring Boot Dockerfile
# Multi-stage build: Maven Wrapper build -> slim JRE runtime
# ============================================================================

FROM eclipse-temurin:25-jdk AS build

WORKDIR /workspace

# Copy Maven wrapper and project configuration first for dependency caching
COPY pom.xml mvnw ./
COPY .mvn .mvn

# Convert Windows CRLF line endings and make Maven wrapper executable
RUN sed -i 's/\r$//' mvnw && \
    chmod +x mvnw

# Download dependencies
RUN ./mvnw -q -B dependency:go-offline || true

# Copy application source code
COPY src src

# Build the application JAR
RUN ./mvnw -q -B -DskipTests package && \
    mv target/reconx-*.jar app.jar


# ============================================================================
# Runtime stage
# ============================================================================

FROM eclipse-temurin:25-jre-alpine

RUN addgroup -S reconx && \
    adduser -S reconx -G reconx

WORKDIR /app

COPY --from=build /workspace/app.jar app.jar

RUN chown reconx:reconx app.jar

USER reconx

EXPOSE 8081

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]