# Dockerfile 최종본

# 1. Build Stage
FROM gradle:8.5-jdk17-alpine AS builder

WORKDIR /app
COPY . .

RUN chmod +x ./gradlew && ./gradlew clean bootJar --no-daemon

# 2. Final Stage
FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Xms256m", "-Xmx400m", "-XX:MaxMetaspaceSize=160m", "-jar", "-Dspring.profiles.active=prod", "/app/app.jar"]

