# syntax=docker/dockerfile:1
# Stage 1: build
FROM amazoncorretto:21-alpine AS build
ARG GRADLE_VERSION=8.10.2
RUN apk add --no-cache curl unzip bash \
 && curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o /tmp/gradle.zip \
 && unzip -q /tmp/gradle.zip -d /opt \
 && ln -s /opt/gradle-${GRADLE_VERSION}/bin/gradle /usr/local/bin/gradle \
 && rm /tmp/gradle.zip
ENV GRADLE_USER_HOME=/root/.gradle-domain
WORKDIR /src

COPY settings.gradle.kts build.gradle.kts gradle.properties ./
RUN --mount=type=cache,target=/root/.gradle-domain \
    gradle --no-daemon dependencies --quiet || true

COPY . .
RUN --mount=type=cache,target=/root/.gradle-domain \
    gradle --no-daemon clean installDist

# Stage 2: runtime
FROM amazoncorretto:21-alpine
WORKDIR /app
COPY --from=build /src/build/install/domain-service /app
EXPOSE 8040
ENTRYPOINT ["/app/bin/domain-service"]
