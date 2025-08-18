# ⬇️ 1단계: 빌드 환경(Gradle + JAVA)
FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

# gradlew와 필요한 파일 먼저 복사
COPY build.gradle settings.gradle gradlew /app/
COPY gradle /app/gradle

# gradlew 실행권한 부여 (1차)
RUN chmod +x /app/gradlew

# 의존성 캐시 및 준비 단계
RUN ./gradlew dependencies --no-daemon || true

# 전체 소스 복사
COPY . /app

# gradlew 실행권한 부여 (2차, 반드시 필요!)
RUN chmod +x /app/gradlew

# JAR 빌드
RUN ./gradlew clean bootJar --no-daemon

# ⬇️ 2단계: 경량 런타임 환경
FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# 빌드 이미지에서 JAR 파일만 복사
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

# 컨테이너 실행 시 JAVA_OPTS 환경변수 동적으로 주입
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
