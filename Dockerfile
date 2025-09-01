FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

COPY build.gradle settings.gradle gradlew /app/
COPY gradle /app/gradle

RUN chmod +x /app/gradlew

# ⬇️ 인증정보 Build Args로 받아서 ENV로 넘김
ARG GH_PACKAGES_USER
ARG GH_PACKAGES_TOKEN
ENV GH_PACKAGES_USER=$GH_PACKAGES_USER
ENV GH_PACKAGES_TOKEN=$GH_PACKAGES_TOKEN

RUN ./gradlew dependencies --no-daemon || true

COPY . /app
RUN chmod +x /app/gradlew

# JAR 빌드
RUN --mount=type=secret,id=gradle,target=/root/.gradle/gradle.properties \
    ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]