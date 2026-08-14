FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY docker/maven-settings.xml /root/.m2/settings.xml
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app

RUN useradd -m -u 1001 appuser \
    && mkdir -p /data/files \
    && chown -R appuser:appuser /data/files

COPY --from=build /app/target/office-online-document-system-1.0.0.jar app.jar

USER appuser
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
