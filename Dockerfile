# syntax=docker/dockerfile:1.7

FROM gradle:8.10.2-jdk17 AS build
WORKDIR /workspace

# Leverage Docker layer caching for dependencies
COPY build.gradle.kts settings.gradle.kts gradlew gradlew.bat ./
COPY gradle ./gradle
RUN chmod +x gradlew
RUN ./gradlew --no-daemon dependencies > /dev/null || true

# Build application
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar

FROM eclipse-temurin:17-jre
WORKDIR /app

ENV JAVA_OPTS=""
COPY --from=build /workspace/build/libs/*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
