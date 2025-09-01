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

ENTRYPOINT ["java", "-jar", \
  "-Dspring.profiles.active=prod", \
  "-Dspring.datasource.url=jdbc:mysql://ticketmongo-db.cpc6yg08m8uo.ap-northeast-2.rds.amazonaws.com:3306/ticketmongo-db", \
  "-Dspring.datasource.username=admin", \
  "-Dspring.datasource.password=mFHukCkW4LtBLIWBlVSy", \
  "-Djwt.secret.key=5Za2EmQNKgi7YbpWJ/l8BSKYBCyv/YMXU6/GVjdrqfw=", \
  "/app/app.jar"]

# ✅ prod 프로필을 직접 지정하여 실행
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "/app/app.jar"]