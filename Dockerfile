# Stage 1: Build
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src src
RUN ./gradlew clean bootJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN addgroup --system app && adduser --system --ingroup app app

COPY --from=builder /app/build/libs/*.jar app.jar

RUN chown app:app app.jar
USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
