FROM maven:3.9.9-eclipse-temurin-8 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:8-jre
WORKDIR /app
RUN mkdir -p /data && chown 10001:10001 /data
COPY --from=build --chown=10001:10001 /build/target/controle-de-caixa-1.0.0-web.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
