---
description: 'Discovery system D1+D2+D4 — persistence, transcript-sampled scoring, batch queue'
label: session-2026-04-23-discovery
---

## Gap
Old Discover was title-keyword bingo on ephemeral results. No feedback loop against `clip_feedback`, no dedup vs imported videos, no batch queue, no persistence.

## D1 — Persistence

- `backend/src/main/resources/schema.sql` — `discovered_video` (status: NEW/QUEUED/IMPORTED/SKIPPED/FAILED, relevance+transcript+predicted scores, FK `job_id`, UNIQUE `youtube_id`) + `discovery_query` (saved searches; D5 stub). Indexes `(status, predicted_score DESC)`, `(status, relevance_score DESC)`, `(youtube_id)`.
- `model/DiscoveredVideo.java` JPA entity; `repository/DiscoveredVideoRepository.java` with `findByYoutubeId`, `findActiveRanked` (NEW+QUEUED sorted by COALESCE(predicted, relevance)).
- `service/DiscoveryService.java` rewritten. `persistResults` upserts by `youtube_id`. Dedup: on upsert, compare against all `video.source_url` YouTube ids → auto-flag status=IMPORTED.
- New endpoints in `controller/DiscoveryController.java`: `GET /candidates?status=`, `POST /candidates/{id}/status`, `POST /queue`, `POST /queue/drain` (drains sequentially into VideoService.importVideo + JobService.createAndStartJob; records resulting jobId back on the candidate row).

## D2 — Transcript-sampled scoring

- `ai-pipeline/discover.py` — new `sample_transcript(url)` runs `yt-dlp --write-auto-subs --skip-download --sub-lang id` (falls back to `en`, then `*`) into a tempdir, parses the VTT into plain text, dedupes rolling-window duplicates. `_vtt_to_text` strips `WEBVTT`/`NOTE`/timing cues/`<c>` tags.
- `predict_clip_potential(transcript, duration, view_count, age_hours)` — calls `features.text.{score_hook_strength, score_keyword_trigger, score_novelty, score_text_sentiment}` on the sample. Transcript score weighted `hook×0.35 + keyword×0.30 + novelty×0.20 + |sentiment-0.5|×2×0.15`. Final predicted = `transcript×0.55 + velocity×0.25 + duration_fit×0.20` (velocity = log10(views/hour)/5; duration_fit sweet spot 3–15 min).
- New CLI mode `--mode enrich --video-url X --duration S --age-hours H --view-count N` returns `{transcriptScore, predictedScore, transcriptSample, transcriptLength}` JSON.
- `DiscoveryService` async-enriches top-10 by relevanceScore on a bounded 3-wide executor pool after each search. Scheduled via `scheduleEnrichment()` at the tail of `persistResults`.

## D4 — Batch queue

- `POST /api/discover/queue` body `{ids:[]}` → bulk `status=QUEUED`.
- `POST /api/discover/queue/drain` → iterates QUEUED candidates, calls `videoService.importVideo(url, null)` + `jobService.createAndStartJob(videoId)`, writes `jobId` + `videoId` back, flips status to IMPORTED.

## SQLite write-lock fix

Enrichment pool + request thread race on `discovered_video` writes. SQLite is single-writer → `SQLITE_BUSY` on ~1/3 of updates under load. Added `private static final Object DB_WRITE_LOCK` in `DiscoveryService`; wrapped every `discoveredRepo.save()` in `synchronized (DB_WRITE_LOCK)`. yt-dlp fetches stay parallel (the real latency). Inside the lock we re-fetch the row before updating to avoid clobbering a concurrent status change (e.g. user queues while we're fetching).

## Frontend

- `frontend/src/lib/api.ts` — new `listCandidates`, `updateCandidateStatus`, `queueCandidates`, `drainCandidateQueue`.
- `frontend/src/app/page.tsx` DiscoveryPanel rewritten. Loads persisted candidates on mount; auto-polls every 3 s while any NEW row still has `transcriptScore === null`. Status filter chips (ACTIVE/NEW/QUEUED/IMPORTED/SKIPPED). Multi-select checkboxes on NEW rows. Shows `predictedScore` when available, else `relevanceScore`, else "enriching…" hint. Skip / Unskip per-row actions. Queue-selected + Process-queue bulk buttons.
- `DiscoveredVideo` TS type expanded: `id, youtubeId, transcriptScore, predictedScore, status, jobId, sourceMode, sourceQuery`.

## Tests

- pytest: `TestDiscoveryVTTParser` (3), `TestPredictClipPotential` (4), `TestDiscoveryEnrichCLI` (1) — 205/205 pass.
- Jest: new test clicks the Discover tab and asserts the persisted-candidate status filter (QUEUED/IMPORTED) renders — 11/11 pass.
- Live smoke: `POST /api/discover/trending` → 20 candidates persisted, 10 enriched async in <40s, 0 SQLITE_BUSY errors after the lock fix. Top candidate scored `transcript=0.805 predicted=0.6428` (1.4M views × high hook density).

## Deferred (planned, not built)

- **D3 — channel trust score.** Aggregate past clip tiers by channel; boost/demote future discovery rows from that channel.
- **D5 — scheduled auto-refresh.** Spring `@Scheduled` re-runs active `discovery_query` rows every N hours.
- **D6 extras — monitor-this-search toggle.** Requires D5.

## Uncommitted
All changes sit on `feat/gpu-optimization-p0-p1-p2`. Frontend `page.tsx` now ~1048 lines (DiscoveryPanel grew ~30 lines net).
