FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY backend/pom.xml .
RUN mvn dependency:go-offline -B
COPY backend/src ./src
RUN mvn package -DskipTests -Dmaven.test.skip=true -B

FROM nvidia/cuda:12.4.1-cudnn-runtime-ubuntu22.04

RUN apt-get update && apt-get install -y --no-install-recommends \
    openjdk-17-jre-headless \
    python3 python3-pip python3-venv \
    ffmpeg curl \
    && curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp \
    -o /usr/local/bin/yt-dlp \
    && chmod +x /usr/local/bin/yt-dlp \
    && rm -rf /var/lib/apt/lists/*

COPY ai-pipeline/requirements.txt /app/ai-pipeline/requirements.txt
RUN python3 -m venv /app/ai-pipeline/.venv \
    && /app/ai-pipeline/.venv/bin/pip install --no-cache-dir -r /app/ai-pipeline/requirements.txt

COPY --from=build /build/target/*.jar /app/app.jar
COPY ai-pipeline/ /app/ai-pipeline/

WORKDIR /app
EXPOSE 8080

ENV PYTHON_PATH=/app/ai-pipeline/.venv/bin/python3
ENV PIPELINE_DIR=/app/ai-pipeline
ENV DATA_DIR=/app/data

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
