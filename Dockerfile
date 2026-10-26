# Build: Maven + JDK 21 (versión fija). Corre en la arquitectura del equipo que construye: el .jar sirve para todas.
FROM --platform=$BUILDPLATFORM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests -Djacoco.skip=true

# Runtime: solo JRE 21, usuario no root, sin secretos (todo entra por variables de entorno)
FROM eclipse-temurin:21.0.8_9-jre-noble
RUN groupadd --system gateway && useradd --system --gid gateway --no-create-home gateway
WORKDIR /app
COPY --from=build /workspace/target/gateway-service-*.jar app.jar
USER gateway
# Solo se publica 8080; el 9090 (Actuator) queda para la red interna (RNF-GW-03).
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=docker
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
