# Multi-stage build: compile with Maven, run on a slim JRE.
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -q -DskipTests package \
 && cp target/can-i-run-it-*-SNAPSHOT.jar /app/can-i-run-it.jar

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN apt-get update \
 && apt-get install -y --no-install-recommends pciutils \
 && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/can-i-run-it.jar /app/can-i-run-it.jar

ENV CAN_I_RUN_IT_BIND=0.0.0.0
EXPOSE 7421

# Default (no args) = ASCII table. Compose overrides this for the web UI.
ENTRYPOINT ["java", "-jar", "/app/can-i-run-it.jar"]
CMD []
