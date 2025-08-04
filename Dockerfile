FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src

RUN mvn clean package

FROM eclipse-temurin:17-jdk

WORKDIR /app

# Copy the built jar from Stage 1
COPY --from=build /build/target/*.jar app.jar

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

EXPOSE 8080