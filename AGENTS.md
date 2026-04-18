# AGENTS.md

## What
YouTube/local video → 9:16 TikTok clips with auto-subtitles. Indonesian language. Local-only.

## Stack
Spring Boot 3.2 (Java 17) + Python 3 CLI + Next.js 14 + SQLite + Docker

## Layout
`backend/` Java · `ai-pipeline/` Python · `frontend/` Next.js · `data/` media · `docs/` specs

## Build Order (strict)
1.Video → 2.Audio → 3.Transcribe → 4.Segment → 5.Score → 6.Render → 7.Subtitle → 8.Variation → 9.Analytics → 10.Preview → 11.Export

## Commands
### Start/Stop (user runs these)
```
.\scripts\start.ps1          # start stack (cached images)
.\scripts\start.ps1 -Build   # start stack (rebuild images)
.\scripts\stop.ps1           # stop all services
```

### Verify
```
.\scripts\verify-env.ps1     # check all dependencies
```

### E2E Testing
```
.\scripts\e2e-test.ps1       # run full E2E suite
```

### Docker
```
docker compose --env-file .env.docker build --no-cache backend
docker compose --env-file .env.docker build --no-cache frontend
docker compose --env-file .env.docker up -d
docker compose --env-file .env.docker down
docker compose --env-file .env.docker logs -f backend
docker compose --env-file .env.docker logs -f frontend
```

### Python Tests
```
cd ai-pipeline
.venv\Scripts\python.exe -m pytest tests\test_pipeline.py -v --cov=. --cov-report=term-missing
```

### Slash Commands
/research · /plan · /execute · /review · /verify · /test

## Key Rules
- Python scripts: JSON stdout envelope `{"success":bool,"data":{}}`, exit 0/non-zero
- When code exists, prefer executable source over plan docs
- Keep it personal-use simple
- **KEEP MEMORY LEAN**: details → journal, memory = pointers only
- Backend `PythonRunner.java` must have `@Component` annotation
- Backend must have CORS config (`WebConfig.java`) allowing `localhost:3000`
- `docker-compose.yml` uses `.env.docker` — always pass `--env-file .env.docker`
- PowerShell scripts: `$ErrorActionPreference = "Continue"` (NOT "Stop" — docker stderr crashes it)
- Use `-UseBasicParsing` on all `Invoke-WebRequest` calls in PowerShell
- Capture docker output to variable, check `$LASTEXITCODE` after, not via pipe
- `clip.rank_pos` not `rank` (SQL reserved word)
- Whisper model: `medium` (RTX 4060 8GB VRAM safe)
- Python venv at `ai-pipeline/.venv`
- Schema auto-creates via `schema.sql`
- SQLite TEXT for UUIDs

## Gate Policy
100% pass + ≥95% coverage + zero type errors. `/test` delegates to test-runner (zen/minimax-m2.5). Binary: OPEN or BLOCKED.

## Workflow
1. User imports YouTube URL → backend downloads → runs 9-stage pipeline
2. User sees progress in real-time (polling `/api/jobs/{id}`)
3. Completed clips shown grouped by source video
4. User can preview, download, export individual clips
5. All state persists in SQLite — refresh does not lose data
6. Frontend loads history on mount from `GET /api/jobs` and `GET /api/videos`

## API Endpoints
| Method | Path | Purpose |
|--------|------|---------|
| GET | /api/health | Health check |
| POST | /api/import | Import video (url or localPath) |
| POST | /api/process | Start pipeline for videoId |
| GET | /api/jobs | List all jobs |
| GET | /api/jobs/{id} | Job detail + stage statuses |
| POST | /api/jobs/{id}/retry | Retry failed job |
| GET | /api/videos | List all videos |
| GET | /api/videos/{id} | Video detail |
| DELETE | /api/videos/{id} | Delete video |
| GET | /api/videos/{id}/clips | List clips for video |
| GET | /api/clips/{id} | Clip detail + score breakdown |
| POST | /api/clips/export | Export clips by IDs |
| GET | /api/clips/{id}/preview | Stream rendered clip |
| GET | /api/clips/{id}/export | Download exported clip |

## Git Strategy
- **Repo**: Monorepo (all in one). GitHub: `raufimusaddiq/viral-clipper` (private)
- **Branch**: `master` is stable. Feature branches: `feat/short-description`
- **Commit style**: Conventional commits — `feat:`, `fix:`, `chore:`, `docs:`, `test:`, `refactor:`
- **Merge**: `git merge --no-ff feat/xxx` (always merge commit, never rebase shared branches)
- **Commit rules**:
  - NEVER commit unless user explicitly asks
  - NEVER push unless user explicitly asks
  - ALWAYS run tests before committing
  - NEVER commit secrets (.env, credentials)
- **Git config (set)**: `pull.rebase true`, `push.autoSetupRemote true`, `merge.conflictStyle zdiff3`, `rerere.enabled true`
- **Use `git switch`** over `git checkout`, `git restore` over `git checkout -- file`

## See journal for: architecture, stack, docker, scoring, decisions, session history
