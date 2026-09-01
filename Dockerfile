# Stage 1: Build the Spring Boot application
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /app

# Copy pom.xml and download dependencies (cached layer)
COPY pom.xml ./
RUN mvn dependency:go-offline -B || true

# Copy source code and build executable JAR
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Lightweight runtime image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Set production environment defaults
ENV PORT=8080
EXPOSE 8080

# Copy the built jar from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Run the Spring Boot application with dynamic PORT binding and memory optimization for Render
ENTRYPOINT ["sh", "-c", "java -Xms128m -Xmx384m -Dserver.port=${PORT:-8080} -Dserver.address=0.0.0.0 -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
