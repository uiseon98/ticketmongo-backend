# 1단계: 빌드 환경 구성
FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

# 빌드에 필요한 파일만 먼저 복사하여 Docker 캐시 활용 극대화
COPY build.gradle settings.gradle gradlew /app/
COPY gradle /app/gradle

# GitHub Actions로부터 인증 정보를 전달받기 위한 ARG 선언
ARG USERNAME
ARG TOKEN

# 의존성 다운로드 (인증 정보는 이 단계에서만 사용)
# --env 플래그를 사용하면 해당 RUN 명령어 내에서만 환경변수가 유효하여 더 안전합니다.
RUN --env USERNAME=$USERNAME --env TOKEN=$TOKEN ./gradlew dependencies --no-daemon

# 나머지 소스 코드 복사
COPY . /app

# JAR 파일 빌드 (이미 의존성은 다운받았으므로 인증 정보 필요 없음)
RUN ./gradlew clean bootJar --no-daemon


# 2단계: 최종 실행 환경 구성
FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# 빌드 환경에서 생성된 JAR 파일만 복사
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]