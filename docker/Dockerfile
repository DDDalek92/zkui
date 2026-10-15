FROM eclipse-temurin:17-jdk-jammy@sha256:43f4431cc895d37ceb115e2e1f160545ec45caee9c1cb4246fc6bb879d58aeda AS build
RUN apt-get update && apt-get install -y --no-install-recommends unzip && rm -rf /var/lib/apt/lists/*
WORKDIR /build
COPY . .
RUN ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:17-jre-jammy@sha256:8993f1aed8b25fcea7a7047a7949c1866fa558fc6830d938c22c4f13b26be9d7
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
