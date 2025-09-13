# Dockerfile 최종본

# Dockerfile 문법 버전을 명시하여 BuildKit 기능을 활성화합니다.
# syntax=docker/dockerfile:1

# 1. Build Stage: Gradle을 사용하여 애플리케이션을 빌드하는 단계
FROM gradle:8.5-jdk17-alpine AS builder

WORKDIR /app
COPY . .

# --mount로 시크릿을 임시 파일(/tmp/github-credentials)로 마운트한 뒤,
# 'source' 명령으로 파일 내용을 현재 쉘의 환경 변수로 로드하고 Gradle을 실행합니다.
RUN --mount=type=secret,id=github-credentials,target=/tmp/github-credentials \
    source /tmp/github-credentials && \
    chmod +x ./gradlew && \
    ./gradlew clean bootJar --no-daemon

# 2. Final Stage: 실제 실행될 이미지를 만드는 단계
FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# builder 스테이지에서 빌드된 JAR 파일만 최종 이미지로 복사합니다.
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

# ✅ prod 프로필을 직접 지정하여 실행
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "/app/app.jar"]