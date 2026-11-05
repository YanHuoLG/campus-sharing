# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build

# Wrapper falls back to tar.gz without unzip, but its pinned SHA-256 is for ZIP.
RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/*

# Use the same Maven version and checksum as local development.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
COPY src/ src/

# Tests use H2; no external MySQL or database credentials are needed here.
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    ./mvnw -B -ntp verify \
    && cp target/campus-sharing-*.jar /build/app.jar

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

RUN groupadd --gid 10001 campus \
    && useradd --uid 10001 --gid campus --no-create-home --shell /usr/sbin/nologin campus \
    && mkdir -p /app/uploads \
    && chown campus:campus /app/uploads

COPY --from=build /build/app.jar /app/app.jar

ENV SERVER_ADDRESS=0.0.0.0 \
    SERVER_PORT=8080 \
    UPLOAD_DIR=/app/uploads \
    JAVA_TOOL_OPTIONS="-Xms128m -Xmx512m -XX:+ExitOnOutOfMemoryError -Djava.awt.headless=true -Duser.timezone=Asia/Shanghai"

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
