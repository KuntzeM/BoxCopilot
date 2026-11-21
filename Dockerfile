# One image: the React SPA is built here and shipped inside the Spring Boot jar (classpath:/static), so the
# browser talks to a single origin. Build from the repository root: docker build -t boxpilot .

# 1) Frontend: Vite production build. Relative URLs only, no build-time API base URL.
FROM node:26.13.1-alpine AS frontend
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# 2) Backend: Maven package with the SPA copied into src/main/resources/static (git-ignored, generated here).
FROM maven:3.9.12-eclipse-temurin-21 AS backend
WORKDIR /workspace
# Dependencies first: this layer is only rebuilt when pom.xml changes.
COPY backend/pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /frontend/dist ./src/main/resources/static
RUN test -f src/main/resources/static/index.html \
 && mvn -B -DskipTests package

# 3) Runtime: JRE only.
FROM eclipse-temurin:21.0.13_9-jre
WORKDIR /app
# Runs as uid/gid 10001 (non-root). Bind mounts from older root-run installs need a one-time chown, see docker-compose.yml.
RUN groupadd --gid 10001 boxpilot \
 && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin boxpilot \
 && mkdir -p /app/logs /var/boxcopilot/images \
 && chown -R boxpilot:boxpilot /app /var/boxcopilot/images
COPY --from=backend --chown=boxpilot:boxpilot /workspace/target/backend.jar ./app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
# HTTP probe with bash (no curl in the JRE image): healthy only if /actuator/health answers "status":"UP".
# HTTP/1.0 makes the server close the connection, `timeout` guarantees the probe cannot hang.
HEALTHCHECK --interval=15s --timeout=5s --start-period=120s --retries=10 \
  CMD timeout 4 bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080; printf "GET /actuator/health HTTP/1.0\r\n\r\n" >&3; grep -q status.:.UP <&3'
USER 10001:10001
ENTRYPOINT ["java","-jar","/app/app.jar"]
