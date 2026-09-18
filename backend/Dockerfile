# Build stage: compile the fat jar with Maven.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies: copy the POM first and download before the source.
COPY pom.xml .
RUN mvn -q dependency:go-offline

COPY src ./src
# Skip tests: Render builds should be fast; the suite runs in CI/dev.
RUN mvn -q package -DskipTests

# Runtime stage: JRE only, non-root.
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --no-create-home spring
USER spring

COPY --from=build /app/target/PaymentGateway-*.jar app.jar

# Render routes traffic to this port; the app must listen on $PORT (default 8080 locally).
ENV PORT=8080
EXPOSE 8080

# Prod profile requires PAYMENTGW_DB_URL / PAYMENTGW_DB_USER / PAYMENTGW_DB_PASSWORD
# (Render env vars); INTERNAL_API_KEY gates the ops endpoints.
# Shell form so ${PORT} expands: Render injects PORT and routes to it.
ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=75 -jar app.jar --spring.profiles.active=prod --server.port=${PORT:-8080}"]
