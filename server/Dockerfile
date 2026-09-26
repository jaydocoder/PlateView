FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.gradle cd server && ./gradlew --no-daemon installDist

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/server/build/install/plateview-server/ ./
COPY plateview-request-restore.sh /opt/plateview/bin/plateview-restore-backup.sh
RUN chmod 0755 /opt/plateview/bin/plateview-restore-backup.sh
EXPOSE 8080
ENTRYPOINT ["bin/plateview-server"]
