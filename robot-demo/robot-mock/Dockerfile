FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -q -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/robot-mock-1.0-SNAPSHOT.jar robot-mock.jar
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "robot-mock.jar"]
