FROM --platform=$BUILDPLATFORM ubuntu:23.10 AS build
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


FROM ubuntu:23.10
COPY --from=build /build/social-media-server/build/distributions/social-media-server-1.0-SNAPSHOT /app/social-media-server
COPY TWRSS /app/twrss

RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    openjdk-21-jre-headless \
    python3 python3-venv; \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app/twrss
RUN python3 -m venv /app/twrss/.venv && . /app/twrss/.venv/bin/activate && pip install --no-cache-dir -r /app/twrss/requirements.txt

EXPOSE 8000

# Set the entry point for the container
ENTRYPOINT ["/app/social-media-server/bin/social-media-server"]

# Pass parameters to the Gradle project
CMD ["/app/twrss/run.sh", "/data", "8000"]
