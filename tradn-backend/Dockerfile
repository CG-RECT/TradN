FROM maven:3.9.9-eclipse-temurin-8 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:8-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && addgroup --system tradn && adduser --system --ingroup tradn tradn
COPY --from=build /build/target/tradn-backend-*.jar app.jar
USER tradn
EXPOSE 8080
ENTRYPOINT ["java","-XX:+UseG1GC","-Djava.security.egd=file:/dev/./urandom","-jar","/app/app.jar"]
