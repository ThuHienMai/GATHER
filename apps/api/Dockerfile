FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S gather && adduser -S gather -G gather
COPY --from=build /build/target/gather-api-*.jar app.jar
USER gather
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
