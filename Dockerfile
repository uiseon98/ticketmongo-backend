# ✅ 1단계: Gradle을 사용하여 JAR 파일을 빌드하는 단계
FROM eclipse-temurin:17-jdk AS build

# 작업 디렉토리 설정
WORKDIR /app

# Gradle 캐시 최적화를 위해 필요한 파일 먼저 복사
COPY build.gradle settings.gradle gradlew /app/
COPY gradle /app/gradle

# Gradle 의존성만 먼저 다운받아 캐싱
RUN ./gradlew dependencies --no-daemon || true

# 전체 소스 복사
COPY . /app

# JAR 빌드
RUN ./gradlew clean bootJar --no-daemon

# ✅ 2단계: 실제 실행 환경 (가벼운 이미지 사용)
FROM eclipse-temurin:17-jdk-alpine

# 작업 디렉토리
WORKDIR /app

# 빌드된 JAR 복사
COPY --from=build /app/build/libs/*.jar app.jar

# 기본 포트 8080 열기
EXPOSE 8080

# JAR 실행 명령
#ENTRYPOINT ["java", "-jar", "app.jar"]
# 환경변수 기반 설정 주입 가능하도록 ENTRYPOINT 수정
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]