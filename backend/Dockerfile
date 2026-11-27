# Etapa 1: compilación con Maven Wrapper dentro del contenedor
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
# Normaliza finales de línea por si el repositorio se clonó en Windows con CRLF
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline
# Agente de OpenTelemetry (observabilidad, numeral 13). Se descarga de Maven Central y queda en la imagen,
# pero solo se activa si el contenedor recibe JAVA_TOOL_OPTIONS=-javaagent:... (ver compose.override.yaml).
ARG OTEL_AGENT_VERSION=2.33.1
RUN ./mvnw -B -q dependency:copy \
      -Dartifact=io.opentelemetry.javaagent:opentelemetry-javaagent:${OTEL_AGENT_VERSION} \
      -DoutputDirectory=/workspace/otel -Dmdep.stripVersion=true
COPY src src
RUN ./mvnw -B -q package -DskipTests

# Etapa 2: imagen de ejecución liviana con usuario sin privilegios
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /workspace/target/inventario-api.jar app.jar
COPY --from=build /workspace/otel/opentelemetry-javaagent.jar otel/opentelemetry-javaagent.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
