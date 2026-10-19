FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src src
RUN mvn -B package -DskipTests
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S inventory && adduser -S inventory -G inventory
WORKDIR /app
COPY --from=build /build/target/inventory-0.1.0.jar app.jar
USER inventory
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
