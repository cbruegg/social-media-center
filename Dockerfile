FROM ubuntu:23.10

RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    openjdk-21-jdk-headless \
    python3 python3-venv \
    unzip; \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

COPY social-media-server /app/social-media-server
COPY TWRSS /app/twrss

WORKDIR /app/social-media-server
RUN ./gradlew assemble
RUN unzip /app/social-media-server/build/distributions/social-media-server-1.0-SNAPSHOT.zip

WORKDIR /app/twrss
RUN python3 -m venv /app/twrss/.venv && . /app/twrss/.venv/bin/activate && pip install --no-cache-dir -r /app/twrss/requirements.txt

EXPOSE 8000

# Set the entry point for the container
ENTRYPOINT ["/app/social-media-server/build/distributions/social-media-server-1.0-SNAPSHOT/bin/social-media-server"]

# Pass parameters to the Gradle project
CMD ["/app/twrss/run.sh", "/data", "8000"]
