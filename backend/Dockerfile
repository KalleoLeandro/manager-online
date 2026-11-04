FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY backend/pom.xml ./
COPY backend/src/ ./src/
COPY database/init.db /database/init.db
RUN --mount=type=cache,target=/root/.m2 mvn -B clean package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/manager-online.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
    CMD curl --fail --silent http://localhost:8080/api/catalog > /dev/null || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
