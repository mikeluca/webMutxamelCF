# Usamos una imagen base ligera de OpenJDK 17
FROM openjdk:17-jdk-slim

# Directorio de trabajo dentro del contenedor
WORKDIR /app

# Copiamos el JAR generado al contenedor
COPY mvc/target/mvc-1.0.jar app.jar

# Exponemos el puerto que usa la app
EXPOSE 8080

# Comando para ejecutar la aplicación
ENTRYPOINT ["java", "-jar", "app.jar"]
