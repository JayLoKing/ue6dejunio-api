# syntax=docker/dockerfile:1
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /workspace
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew
COPY config ./config
COPY src ./src
RUN ./gradlew --no-daemon bootJar && \
    find build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' > /tmp/jars && \
    test "$(wc -l < /tmp/jars)" -eq 1 && \
    cp "$(cat /tmp/jars)" /application.jar

FROM eclipse-temurin:25-jre-noble
RUN groupadd --gid 10001 app && useradd --uid 10001 --gid app --no-create-home app
WORKDIR /app
COPY --from=build --chown=app:app /application.jar ./application.jar
COPY --chmod=755 docker/api-healthcheck.sh /usr/local/bin/api-healthcheck
ENV SPRING_PROFILES_ACTIVE=prod TZ=America/La_Paz
USER 10001:10001
EXPOSE 8090
# TCP only: proves the listener exists, not database/model or authenticated workflows.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 CMD ["api-healthcheck"]
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
