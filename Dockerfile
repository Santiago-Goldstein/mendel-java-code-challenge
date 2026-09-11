FROM maven:3.9.16-eclipse-temurin-17-alpine AS build

WORKDIR /app

COPY pom.xml .

RUN mvn dependency:go-offline -B

COPY src ./src

RUN mvn clean package -B


FROM eclipse-temurin:17.0.20_8-jre-alpine-3.24

WORKDIR /app

RUN addgroup -S spring \
    && adduser -S spring -G spring

COPY --from=build /app/target/transactions-0.0.1-SNAPSHOT.jar app.jar

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]