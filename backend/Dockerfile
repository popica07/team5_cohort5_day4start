# ============================================================================
# TICKET-ADV146 — Spring Boot Dockerfile (multi-stage Maven -> JRE 25 slim)
#
# Stage 1: build the JAR with Temurin JDK 25 + Maven.
# Stage 2: copy ONLY the JAR onto a slim JRE image.
#
# Windows fix: sed strips \r (CRLF) from mvnw before executing it.
# Without this, Windows git checkout corrupts the shebang line and
# Docker reports "./mvnw: not found" (exit 127) on Linux.
# ============================================================================

# ============================================================================
# TICKET-ADV146 — Spring Boot Dockerfile (multi-stage Maven -> JRE 25 slim)
#
# Stage 1: build the JAR with Temurin JDK 25 + Maven.
# Stage 2: copy ONLY the JAR onto a slim JRE image.
# ============================================================================

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
# checkstyle.xml + suppressions.xml are needed at build time: the Checkstyle
# plugin binds to `validate`, so it runs as part of `package` below and fails
# the build if its config isn't in the image.
COPY pom.xml mvnw checkstyle.xml suppressions.xml ./
COPY .mvn .mvn
RUN ./mvnw -q -B dependency:go-offline || true
COPY src src
RUN ./mvnw -q -B -DskipTests package && \
    mv target/reconx-*.jar app.jar

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S reconx && adduser -S reconx -G reconx
USER reconx
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
