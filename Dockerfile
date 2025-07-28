FROM openjdk:17-jdk-slim
WORKDIR /app
COPY mvc/target/mvc-1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
