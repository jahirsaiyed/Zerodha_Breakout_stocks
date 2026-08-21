# =============================================================================
# Stage 1: Build — compile and package the Spring Boot application
# =============================================================================
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Copy Maven wrapper and project descriptor first (layer cache)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Make mvnw executable and download dependencies (cached if pom.xml unchanged)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source and build the fat JAR, skipping tests (tests run in CI)
COPY src/ src/
RUN ./mvnw package -DskipTests -B

# =============================================================================
# Stage 2: Runtime — minimal JRE image
# =============================================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Create a non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy the executable JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

# Switch to non-root user
USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
