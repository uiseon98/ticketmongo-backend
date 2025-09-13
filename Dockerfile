# Dockerfile 최종본

# Dockerfile 문법 버전을 명시하여 BuildKit 기능을 활성화합니다.
# syntax=docker/dockerfile:1

# 1. Build Stage: Gradle을 사용하여 애플리케이션을 빌드하는 단계
FROM gradle:8.5-jdk17-alpine AS builder

WORKDIR /app
COPY . .

# Alpine 이미지에는 bash가 기본 포함되어 있지 않으므로, apk를 이용해 설치합니다.
# --no-cache 옵션은 이미지 크기를 작게 유지하는 데 도움을 줍니다.
RUN apk add --no-cache bash

# RUN 명령 전체를 bash -c "..." 로 감싸서 bash 쉘에서 실행되도록 변경합니다.
# 이렇게 하면 'source' 명령어를 정상적으로 사용할 수 있습니다.
RUN --mount=type=secret,id=github-credentials,target=/tmp/github-credentials \
    bash -c "source /tmp/github-credentials && \
    chmod +x ./gradlew && \
    ./gradlew clean bootJar --no-daemon"

# 2. Final Stage: 실제 실행될 이미지를 만드는 단계
FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# builder 스테이지에서 빌드된 JAR 파일만 최종 이미지로 복사합니다.
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

# ✅ prod 프로필을 직접 지정하여 실행
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "/app/app.jar"]