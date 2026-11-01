FROM maven:3.9.12-eclipse-temurin-21 AS build
WORKDIR /workspace
# Dependencies first: this layer is only rebuilt when pom.xml changes.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21.0.13_9-jre
WORKDIR /app
# Non-root runtime. docker-compose bind-mounts ./logs and ./images; those host directories must be writable
# by uid/gid 10001 (one-time: sudo chown -R 10001:10001 logs images). See README, "Upgrading to the Boot 4 image".
RUN groupadd --gid 10001 boxpilot \
 && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin boxpilot \
 && mkdir -p /app/logs /var/boxcopilot/images \
 && chown -R boxpilot:boxpilot /app /var/boxcopilot/images
COPY --from=build --chown=boxpilot:boxpilot /workspace/target/backend-0.1.0.jar ./app.jar
USER boxpilot
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
# TCP probe with bash (no curl in the JRE image, no actuator dependency = no extra endpoint to expose).
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD ["bash","-c","exec 3<>/dev/tcp/127.0.0.1/8080"]
ENTRYPOINT ["java","-jar","/app/app.jar"]
