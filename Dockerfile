# Multi-stage build for production
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Copy Gradle files
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle

# Download dependencies (cached layer)
RUN ./gradlew dependencies --no-daemon

# Copy source code
COPY src ./src

# Build application
RUN ./gradlew build -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy built JAR
COPY --from=builder /app/build/libs/*.jar app.jar

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]
