FROM eclipse-temurin:21-jre
WORKDIR /app
ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar
EXPOSE 8004
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
