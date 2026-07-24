# Build
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

# Runtime
FROM eclipse-temurin:17-jre
WORKDIR /app
ENV SPRING_PROFILES_ACTIVE=cloud
ENV JAVA_TOOL_OPTIONS="-Xmx350m"
COPY --from=build /app/target/sga-alquileres.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
