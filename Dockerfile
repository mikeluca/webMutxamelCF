# INF-01: imagen previa (openjdk:17-jdk-slim) esta deprecada en Docker Hub
# desde julio de 2022 y sin parches; ejecutaba como root, con el JDK
# completo (no solo el runtime), sin HEALTHCHECK, y exigia compilar el jar
# fuera de la imagen.
#
# Build multi-stage: la primera fase compila dentro de la imagen (no hace
# falta tener Maven instalado en el host ni en el VPS); la segunda copia
# solo el jar final a una imagen JRE (no JDK) minima, y ejecuta como un
# usuario sin privilegios.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY . .
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:17-jre-jammy

# curl para el HEALTHCHECK: la imagen jre-jammy no lo trae de serie.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /src/mvc/target/mvc-1.0.jar app.jar
USER app

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=30s \
    CMD curl -f http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
