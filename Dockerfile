# --- Build stage: Gradle wrapper on a full JDK 25 (Temurin: the Corretto image lacks xargs, which gradlew needs) ---
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
# gradle.properties (local JDK path) is excluded by .dockerignore, so the image JDK is used as is.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN chmod +x gradlew
COPY src src
RUN ./gradlew --no-daemon bootJar -x test && cp build/libs/*-SNAPSHOT.jar app.jar

# --- Run stage: small JRE 25 ---
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --no-create-home app
COPY --from=build /workspace/app.jar app.jar
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1 -Dspring.profiles.active=prod"
ENTRYPOINT ["java", "-jar", "app.jar"]
