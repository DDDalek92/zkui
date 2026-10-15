FROM eclipse-temurin:8-jdk-jammy@sha256:a42a161e0edc7f0d3815bcfa22ce6d969fb48f69d0d17d4a1affd9800b455385 AS build
RUN apt-get update && apt-get install -y --no-install-recommends unzip && rm -rf /var/lib/apt/lists/*
WORKDIR /build
COPY . .
RUN ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:8-jre-jammy@sha256:bbd3fa148819097f0bd441524ef72cf730341faa89d137059252581dbcc58ab0
WORKDIR /var/app
RUN mkdir -p /var/app/data && chown -R 10001:10001 /var/app
COPY --from=build --chown=10001:10001 /build/target/zkui-2.0-SNAPSHOT-jar-with-dependencies.jar /var/app/zkui.jar
COPY --chown=10001:10001 config.cfg /var/app/config.cfg
COPY --chown=10001:10001 docker/bootstrap.sh /var/app/bootstrap.sh
RUN sed -i 's|^jdbcUrl=.*|jdbcUrl=jdbc:h2:/var/app/data/history|' /var/app/config.cfg
USER 10001:10001
VOLUME ["/var/app/data"]
EXPOSE 9090
ENTRYPOINT ["sh", "/var/app/bootstrap.sh"]
