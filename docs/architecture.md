# Architecture

## Overview

Minimal local architecture: 1 Spring Boot backend + 1 worker process.

```
[Next.js UI]  ←→  [Spring Boot API]  →  [SQLite]
                        |
                  [Worker Thread]
                        |
           +------------+------------+
           |            |            |
    [yt-dlp/ffmpeg] [Python CLI] [ffmpeg/OpenCV]
           |            |            |
      media files  transcripts   rendered clips
           |            |            |
           +------ data/ folder -----+
```

## Component Responsibilities

### Spring Boot Backend (`backend/`)
- REST API for the Next.js frontend
- Job orchestration: manages the pipeline stages
- Calls Python CLI scripts as subprocess (JSON stdin/stdout)
- Calls yt-dlp and ffmpeg as subprocess
- SQLite for all metadata (jobs, videos, clips, scores)
- Serves static media files from `data/` for preview

### Python AI Pipeline (`ai-pipeline/`)
- Standalone CLI scripts, each doing one job
- Called by Spring Boot via `ProcessBuilder`
- **Input**: JSON on stdin + file paths as args
- **Output**: JSON on stdout
- **Exit code**: 0 = success, non-zero = failure
- Scripts: `transcribe.py`, `segment.py`, `score.py`
- Uses CUDA for faster-whisper and OpenCV GPU ops

### Next.js Frontend (`frontend/`)
- Runs on localhost, connects to Spring Boot API
- Video input (paste URL or upload file)
- Job status display
- Clip list with scores and ranking
- Video preview player
- Export/download controls

## Data Flow (Happy Path)

```
1. User pastes YouTube URL in UI
2. Frontend → POST /api/import { url }
3. Backend creates Job (status=QUEUED), saves to SQLite
4. Backend starts worker:
   a. yt-dlp downloads video → data/raw/{videoId}.mp4
   b. ffmpeg extracts audio → data/audio/{videoId}.wav
   c. Python transcribe.py → data/transcripts/{videoId}.json
   d. Python segment.py → data/segments/{videoId}.json
   e. Python score.py → updates segments with scores
   f. ffmpeg crops + renders each top clip → data/renders/{clipId}.mp4
   g. ffmpeg burns subtitles → data/exports/{clipId}.mp4
5. Backend updates Job status=COMPLETED
6. Frontend polls GET /api/jobs/{id} and shows results
7. User previews clips, picks favorites, exports
```

## Tech Decisions

| Decision | Choice | Why |
|----------|--------|-----|
| Architecture | Minimal (1 backend + 1 worker) | Personal use, simplest to build and debug |
| Java↔Python bridge | CLI subprocess + JSON | No extra service, no network dependency, simple |
| DB | SQLite | Zero config, local-only, file-based |
| GPU | CUDA | Available; significantly speeds up transcription |
| Transcription | faster-whisper | Best local Whisper implementation, CUDA support |
| Frontend | Next.js | User preference, good DX for interactive UIs |
| Video download | yt-dlp | Standard tool, handles all YouTube formats |
| Video processing | FFmpeg + OpenCV | FFmpeg for codec/crop, OpenCV for face detection/crop |
| Subtitle burn-in | FFmpeg drawtext | Simple, no external subtitle library needed |
| Content language | Indonesian | Target audience is Indonesian TikTok |

## Pipeline Stages

Each stage reads from `data/` subfolder and writes output to the next subfolder. A job tracks which stage it's on.

| Stage | Input | Process | Output |
|-------|-------|---------|--------|
| 1. Import | URL or file path | yt-dlp / file copy | `data/raw/{videoId}.mp4` |
| 2. Audio | raw video | ffmpeg extract | `data/audio/{videoId}.wav` |
| 3. Transcribe | audio WAV | faster-whisper (CUDA) | `data/transcripts/{videoId}.json` |
| 4. Segment | transcript JSON | Python segment logic | `data/segments/{videoId}.json` |
| 5. Score | segments JSON | Python scoring | updates segments JSON |
| 6. Render | raw video + segments | ffmpeg center-crop | `data/renders/{clipId}.mp4` |
| 7. Subtitle | render + transcript | ffmpeg drawtext | `data/exports/{clipId}.mp4` |

## Error Handling

- Any pipeline stage failure → Job status = FAILED, error message stored
- Frontend shows error with retry option
- Python scripts write errors to stderr (not stdout) to keep JSON output clean
- Backend captures subprocess stdout/stderr separately

## Concurrency

- MVP: one job at a time (sequential pipeline)
- Job queue is in-memory, FIFO
- Future: could add parallel clip rendering if needed
