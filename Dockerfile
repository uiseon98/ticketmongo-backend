# Dockerfile 최종본

FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

COPY build.gradle settings.gradle gradlew /app/
COPY gradle /app/gradle
COPY . /app

RUN chmod +x /app/gradlew

# GitHub Actions로부터 인증 정보를 전달받기 위한 ARG 선언
ARG USERNAME
ARG TOKEN

# JAR 빌드 시, 전달받은 ARG를 환경변수로 사용하여 Gradle 실행
RUN USERNAME=$USERNAME TOKEN=$TOKEN ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

# ✅ prod 프로필을 직접 지정하여 실행
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "/app/app.jar"]