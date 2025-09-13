# Dockerfile 최종본

# 1. Build Stage
FROM gradle:8.5-jdk17-alpine AS builder

WORKDIR /app

# ARG로 GitHub 인증 정보를 받습니다.
ARG USERNAME
ARG TOKEN

# 1. 먼저 프로젝트의 모든 파일을 복사합니다.
COPY . .

# 2. 복사된 프로젝트 내의 gradle.properties 파일에 인증 정보를 '덧붙입니다' (>>)
#    이렇게 하면 기존 파일이 있든 없든, 인증 정보가 확실하게 추가됩니다.
RUN echo "\ngithubUser=${USERNAME}" >> /app/gradle.properties && \
    echo "githubToken=${TOKEN}" >> /app/gradle.properties

# 3. 별도의 인증 정보 전달 없이 Gradle을 실행합니다.
RUN chmod +x ./gradlew && ./gradlew clean bootJar --no-daemon


# 2. Final Stage
FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# builder 스테이지에서 빌드된 JAR 파일만 최종 이미지로 복사합니다.
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "/app/app.jar"]