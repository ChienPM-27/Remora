FROM gradle:8.11-jdk21-alpine AS build
WORKDIR /home/gradle/src
COPY --chown=gradle:gradle . .
RUN gradle :ai-service:installDist --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /home/gradle/src/ai-service/build/install/ai-service /app
EXPOSE 8082
ENTRYPOINT ["/app/bin/ai-service"]
