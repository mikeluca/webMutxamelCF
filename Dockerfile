# Usar una imagen base con JDK 17
FROM openjdk:17-alpine

# Directorio de trabajo dentro del contenedor
WORKDIR /app

# Copiar el JAR al contenedor
COPY target/*.jar app.jar

# Puerto que expone tu app
EXPOSE 8080

# Comando para arrancar la app
ENTRYPOINT ["java", "-jar", "app.jar"]
