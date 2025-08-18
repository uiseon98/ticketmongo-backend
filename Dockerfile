# ✅ 1단계: Gradle을 사용하여 애플리케이션 JAR 파일을 빌드하는 단계
FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

# gradlew, build파일들 먼저 복사
COPY build.gradle settings.gradle gradlew /app/
COPY gradle /app/gradle

# ⬇️ gradlew 실행권한 반드시 추가!
RUN chmod +x /app/gradlew

# Gradle 의존성 먼저 캐싱
RUN ./gradlew dependencies --no-daemon || true

# 앱 전체 복사
COPY . /app

# 빌드 타임 ARG, ENV 구문에서 * 모두 제거 (정상 문법)
ARG GH_PACKAGES_USER
ARG GH_PACKAGES_TOKEN

ENV GH_PACKAGES_USER=$GH_PACKAGES_USER
ENV GH_PACKAGES_TOKEN=$GH_PACKAGES_TOKEN

# Spring Boot JAR 빌드
RUN ./gradlew clean bootJar --no-daemon

# ✅ 2단계: 경량 실행 환경
FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# 빌드 스테이지에서 만든 jar 파일 복사
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

# ENTRYPOINT에서 * 제거 (정상 문법)
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
