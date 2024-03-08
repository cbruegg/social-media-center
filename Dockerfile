FROM --platform=$BUILDPLATFORM ubuntu:23.10 AS buildServer
ARG TARGETPLATFORM
ARG BUILDPLATFORM

RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    openjdk-21-jdk-headless \
    unzip; \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

COPY social-media-server /build/social-media-server

WORKDIR /build/social-media-server
RUN ./gradlew assemble
RUN unzip /build/social-media-server/build/distributions/social-media-server-1.0-SNAPSHOT.zip

FROM --platform=x86_64 ubuntu:23.10 AS buildWebApp
# We need to run this on x86_64 as there's no Kotlin/Native compiler for Linux aarch64

RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    openjdk-21-jdk-headless; \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

COPY SocialMediaCenter /build/SocialMediaCenter

WORKDIR /build/SocialMediaCenter
RUN ./gradlew wasmJsBrowserDistribution

FROM alpine:3.19.1
COPY --from=buildServer /build/social-media-server/social-media-server-1.0-SNAPSHOT /app/social-media-server
COPY --from=buildWebApp /build/SocialMediaCenter/composeApp/build/dist/wasmJs/productionExecutable /app/social-media-server/SocialMediaCenterWeb
COPY TWRSS /app/twrss
COPY container-entrypoint.sh /app/container-entrypoint.sh

RUN apk add --no-cache \
    bash \
    python3 \
    py3-pip \
    openjdk21-jre-headless

WORKDIR /app/twrss
RUN python3 -m venv /opt/venv
RUN . /opt/venv/bin/activate && pip3 install --no-cache-dir -r /app/twrss/requirements.txt

EXPOSE 8000

ENTRYPOINT ["/app/container-entrypoint.sh"]
CMD ["/app/twrss/run.sh", "/data", "8000", "/app/social-media-server/SocialMediaCenterWeb"]
