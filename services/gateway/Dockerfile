FROM gradle:8.11-jdk21-alpine AS build
WORKDIR /home/gradle/src
COPY --chown=gradle:gradle . .
RUN gradle :gateway:installDist --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /home/gradle/src/gateway/build/install/gateway /app
EXPOSE 8080
ENTRYPOINT ["/app/bin/gateway"]
