# syntax=docker/dockerfile:1
FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml build-maven.sh ./
COPY src src
RUN --mount=type=cache,target=/root/.m2 --mount=type=secret,id=maven_settings --mount=type=secret,id=java_cacerts sh build-maven.sh package -DskipTests
FROM eclipse-temurin:21-jre-jammy
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /app/target/negocio-facil-0.1.0.jar app.jar
COPY --from=build /app/target/classes/com/negociofacil/config/HealthProbe.class health/com/negociofacil/config/HealthProbe.class
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
