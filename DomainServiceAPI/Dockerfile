# Stage 1: build
FROM amazoncorretto:21-alpine AS build
ARG GRADLE_VERSION=8.10.2
RUN apk add --no-cache curl unzip bash \
 && curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o /tmp/gradle.zip \
 && unzip -q /tmp/gradle.zip -d /opt \
 && ln -s /opt/gradle-${GRADLE_VERSION}/bin/gradle /usr/local/bin/gradle \
 && rm /tmp/gradle.zip
WORKDIR /src
COPY . .
RUN gradle --no-daemon clean buildFatJar

# Stage 2: runtime
FROM amazoncorretto:21-alpine
WORKDIR /app
COPY --from=build /src/build/libs/*-all.jar /app/app.jar
EXPOSE 8040
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
