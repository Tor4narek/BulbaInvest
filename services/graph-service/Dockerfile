FROM gradle:8.7-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle installDist --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/install/graph-service /app
EXPOSE 8083
ENTRYPOINT ["/app/bin/graph-service"]

