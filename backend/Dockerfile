FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY backend/pom.xml .
RUN mvn dependency:go-offline -B
COPY backend/src ./src
RUN mvn package -DskipTests -Dmaven.test.skip=true -B

FROM nvidia/cuda:12.4.1-cudnn-runtime-ubuntu22.04

# Ubuntu 22.04 LTS provides de-facto version pinning for the lifetime of the
# release, so we don't further pin these (explicit = stale within months;
# yt-dlp especially breaks on YouTube changes if frozen).
RUN apt-get update && apt-get install -y --no-install-recommends \
    openjdk-17-jre-headless \
    python3 python3-pip python3-venv \
    ffmpeg \
    curl \
    && rm -rf /var/lib/apt/lists/*
# curl is needed at runtime by the docker-compose healthcheck
# (`curl -f http://localhost:8080/api/health`). It's also generally useful
# for debugging an attached shell. Tiny package — no reason to skip it.

COPY ai-pipeline/requirements.txt /app/ai-pipeline/requirements.txt
RUN python3 -m venv /app/ai-pipeline/.venv \
    && /app/ai-pipeline/.venv/bin/pip install --no-cache-dir -r /app/ai-pipeline/requirements.txt

ARG WHISPER_MODEL=mobiuslabsgmbh/faster-whisper-large-v3-turbo
RUN /app/ai-pipeline/.venv/bin/python3 -c "from faster_whisper import WhisperModel; WhisperModel('${WHISPER_MODEL}', device='cpu', compute_type='int8')"

COPY --from=build /build/target/*.jar /app/app.jar
COPY ai-pipeline/ /app/ai-pipeline/

WORKDIR /app
EXPOSE 8080

# Put the venv bin on PATH so the `yt-dlp` shim installed by pip resolves
# without callers having to know the venv layout.
ENV PATH=/app/ai-pipeline/.venv/bin:$PATH
ENV PYTHON_PATH=/app/ai-pipeline/.venv/bin/python3
ENV PIPELINE_DIR=/app/ai-pipeline
ENV DATA_DIR=/app/data

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
