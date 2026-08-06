FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon installDist

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/build/install/plateview-server/ ./
EXPOSE 8080
ENTRYPOINT ["bin/plateview-server"]
