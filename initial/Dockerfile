# Stap 1: bouwen
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stap 2: uitvoeren
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# OTel agent downloaden tijdens image build
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar opentelemetry-javaagent.jar

EXPOSE 8080
ENTRYPOINT ["java", \
  "-javaagent:opentelemetry-javaagent.jar", \
  "-Dotel.service.name=gs-spring-boot", \
  "-Dotel.exporter.otlp.endpoint=http://otel-collector:4318", \
  "-Dotel.exporter.otlp.protocol=http/protobuf", \
  "-Dotel.instrumentation.jms.enabled=true", \
  "-Dotel.metrics.exporter=otlp", \
  "-Dotel.traces.exporter=otlp", \
  "-Dotel.logs.exporter=none", \
  "-jar", "app.jar"]
