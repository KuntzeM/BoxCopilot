FROM maven:3.9.12-eclipse-temurin-21 AS build
WORKDIR /workspace
# Dependencies first: this layer is only rebuilt when pom.xml changes.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21.0.13_9-jre
WORKDIR /app
# Runs as uid/gid 10001 (non-root). Bind mounts from older root-run installs need a one-time chown, see docker-compose.yml.
RUN groupadd --gid 10001 boxpilot \
 && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin boxpilot \
 && mkdir -p /app/logs /var/boxcopilot/images \
 && chown -R boxpilot:boxpilot /app /var/boxcopilot/images
COPY --from=build --chown=boxpilot:boxpilot /workspace/target/backend.jar ./app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
# HTTP probe with bash (no curl in the JRE image): healthy only if /actuator/health answers "status":"UP".
# HTTP/1.0 makes the server close the connection, `timeout` guarantees the probe cannot hang.
HEALTHCHECK --interval=15s --timeout=5s --start-period=120s --retries=10 \
  CMD timeout 4 bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080; printf "GET /actuator/health HTTP/1.0\r\n\r\n" >&3; grep -q status.:.UP <&3'
USER 10001:10001
ENTRYPOINT ["java","-jar","/app/app.jar"]
