# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/

RUN chmod +x ./mvnw \
    && ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy AS runtime

ARG TARGETARCH
ARG SYFT_VERSION=1.44.0

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        git \
    && rm -rf /var/lib/apt/lists/*

RUN set -eux; \
    case "${TARGETARCH}" in \
        amd64|arm64) ;; \
        *) echo "Unsupported TARGETARCH: ${TARGETARCH}" >&2; exit 1 ;; \
    esac; \
    syft_asset="syft_${SYFT_VERSION}_linux_${TARGETARCH}.tar.gz"; \
    syft_checksums="syft_${SYFT_VERSION}_checksums.txt"; \
    curl -fsSLo "/tmp/${syft_asset}" "https://github.com/anchore/syft/releases/download/v${SYFT_VERSION}/${syft_asset}"; \
    curl -fsSLo "/tmp/${syft_checksums}" "https://github.com/anchore/syft/releases/download/v${SYFT_VERSION}/${syft_checksums}"; \
    cd /tmp; \
    grep " ${syft_asset}$" "${syft_checksums}" | sha256sum -c -; \
    tar -xzf "${syft_asset}" -C /usr/local/bin syft; \
    chmod +x /usr/local/bin/syft; \
    rm -f "/tmp/${syft_asset}" "/tmp/${syft_checksums}"; \
    syft version

WORKDIR /app

RUN groupadd --system app \
    && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app \
    && mkdir -p /app/output \
    && chown -R app:app /app

COPY --from=build --chown=app:app /workspace/target/*.jar /app/app.jar

ENV SERVER_PORT=8080 \
    SPRING_PROFILES_ACTIVE=sandbox \
    SBOM_SYFT_PATH=/usr/local/bin/syft

EXPOSE 8080

USER app

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
