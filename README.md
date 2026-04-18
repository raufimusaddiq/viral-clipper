# Viral Clipper

AI-powered video clip maker for Indonesian TikTok content. Imports YouTube/local videos, transcribes with Whisper (GPU), segments into clips, scores for viral potential, renders 9:16 vertical clips with auto-subtitles.

## Stack

| Component | Tech |
|-----------|------|
| Backend API | Spring Boot 3.2 (Java 17) |
| AI Pipeline | Python 3 + faster-whisper (CUDA) |
| Frontend | Next.js 14 |
| Database | SQLite |
| Deployment | Docker Compose + NVIDIA GPU |

## Architecture

```
YouTube URL ──► Backend API ──► 9-Stage Pipeline
                                    │
                    1. Import Video (yt-dlp)
                    2. Extract Audio (ffmpeg)
                    3. Transcribe (Whisper medium, CUDA)
                    4. Segment (gap-based)
                    5. Score (10-feature weighted formula)
                    6. Render 9:16 (ffmpeg)
                    7. Burn Subtitles (ffmpeg drawtext)
                    8. Generate Variations (zoom/crop presets)
                    9. Analytics (distribution, recommendations)
                                    │
                              Frontend UI ◄── polling
```

## Prerequisites

- Docker Desktop with WSL2 backend
- NVIDIA GPU + [Container Toolkit](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/install-guide.html)
- PowerShell 5.1+

## Quick Start

```powershell
# 1. Clone
git clone https://github.com/<your-user>/viral-clipper.git
cd viral-clipper

# 2. Copy env template (defaults work for Docker)
copy .env.example .env

# 3. Start everything
.\scripts\start.ps1 -Build

# 4. Open in browser
# Frontend: http://localhost:3000
# Backend:  http://localhost:8080/api/health
```

## Commands

```powershell
.\scripts\start.ps1              # start (cached images)
.\scripts\start.ps1 -Build       # start (rebuild images)
.\scripts\stop.ps1               # stop all services
.\scripts\verify-env.ps1         # check dependencies
.\scripts\e2e-test.ps1           # run E2E test suite
```

## Usage

1. Open http://localhost:3000
2. Paste a YouTube URL → **Import & Process**
3. Pipeline runs all 9 stages automatically
4. Browse clips grouped by video, filter by tier (Viral / Potential / Skip)
5. Preview clips in-app, download or export with subtitles

## Scoring

Clips are scored on 10 weighted features with Indonesian keyword heuristics:

| Feature | Weight | Description |
|---------|--------|-------------|
| Hook Strength | 20% | Question/hook phrase in opening |
| Keyword Trigger | 10% | Indonesian viral keywords |
| Novelty | 10% | Numbers, proper nouns, step words |
| Clarity | 10% | Duration + context words |
| Emotional Energy | 10% | Energy level (placeholder for ML) |
| Face Presence | 10% | Face detection score |
| Pause Structure | 7% | Speech rhythm quality |
| Scene Change | 8% | Visual change rate |
| Topic Fit | 8% | Niche keyword match |
| History Score | 7% | Historical performance |

Boosts: sharp questions, opinion conflicts, numbered lists, emotional moments
Penalties: slow openings, excessive silence, generic content, no face

Tiers: **PRIMARY** (>= 0.80), **BACKUP** (>= 0.65), **SKIP** (< 0.65)

## Project Layout

```
├── backend/             Spring Boot API + pipeline orchestrator
│   ├── src/main/java/   Controllers, services, models, config
│   └── Dockerfile       CUDA base + Java + Python + ffmpeg
├── ai-pipeline/         Python CLI scripts (stages 1-9)
│   ├── transcribe.py    Whisper transcription
│   ├── segment.py       Gap-based segmentation
│   ├── score.py         10-feature viral scoring
│   ├── render.py        9:16 ffmpeg rendering
│   ├── subtitle.py      Word-level subtitle burning
│   ├── variation.py     Zoom/crop variation generator
│   ├── analytics.py     Distribution + recommendations
│   ├── utils/           Audio/video analysis helpers
│   └── tests/           126 tests, 90% coverage
├── frontend/            Next.js 14 web UI
│   └── src/app/         Video grouping, clip cards, transcript view
├── scripts/             PowerShell automation
├── docs/                Architecture & API specs
├── docker-compose.yml   Full stack orchestration
└── AGENTS.md            AI agent instructions & conventions
```

## Configuration

Copy `.env.example` to `.env` for local overrides. Docker uses `.env.docker` (committed defaults).

| Variable | Default | Description |
|----------|---------|-------------|
| `WHISPER_MODEL` | medium | Whisper model size |
| `WHISPER_DEVICE` | cuda | cuda or cpu |
| `WHISPER_LANGUAGE` | id | Language code (Indonesian) |
| `NICHE_KEYWORDS` | rahasia,penting,trik,solusi,tip | Scoring keywords |

## API

| Method | Path | Purpose |
|--------|------|---------|
| GET | /api/health | Health check |
| POST | /api/import | Import video |
| POST | /api/process | Start pipeline |
| GET | /api/jobs | List all jobs |
| GET | /api/jobs/{id} | Job + stage statuses |
| GET | /api/videos | List all videos |
| GET | /api/videos/{id}/clips | Clips for video |
| GET | /api/clips/{id} | Clip + score breakdown |
| GET | /api/clips/{id}/preview | Stream rendered clip |
| GET | /api/clips/{id}/export | Download exported clip |

## License

Private project. All rights reserved.
