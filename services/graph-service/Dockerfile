FROM gradle:8.7-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle shadowJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*-all.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
