# REST API Specification

Base URL: `http://localhost:8080/api`

All request/response bodies are JSON. All responses follow this envelope:

```json
{
  "status": "ok" | "error",
  "data": { ... },
  "error": "optional error message"
}
```

---

## Import Video

Start a new job by importing a YouTube URL or local file.

```
POST /api/import
```

**Request:**
```json
{
  "url": "https://www.youtube.com/watch?v=...",
  "localPath": null
}
```

One of `url` or `localPath` is required. If both provided, `url` takes priority.

**Response:**
```json
{
  "status": "ok",
  "data": {
    "jobId": "uuid",
    "videoId": "uuid",
    "status": "QUEUED"
  }
}
```

---

## Process Video

Start the full pipeline for a previously imported video. Runs all stages sequentially.

```
POST /api/process
```

**Request:**
```json
{
  "videoId": "uuid"
}
```

**Response:**
```json
{
  "status": "ok",
  "data": {
    "jobId": "uuid",
    "status": "RUNNING",
    "currentStage": "TRANSCRIBING"
  }
}
```

---

## Get Job Status

```
GET /api/jobs/{jobId}
```

**Response:**
```json
{
  "status": "ok",
  "data": {
    "jobId": "uuid",
    "videoId": "uuid",
    "status": "RUNNING",
    "currentStage": "SCORING",
    "stages": {
      "IMPORT": "COMPLETED",
      "AUDIO_EXTRACT": "COMPLETED",
      "TRANSCRIBE": "COMPLETED",
      "SEGMENT": "COMPLETED",
      "SCORE": "IN_PROGRESS",
      "RENDER": "PENDING",
      "SUBTITLE": "PENDING"
    },
    "createdAt": "2026-04-16T22:00:00",
    "updatedAt": "2026-04-16T22:05:00",
    "errorMessage": null
  }
}
```

Job statuses: `QUEUED`, `RUNNING`, `COMPLETED`, `FAILED`

Stage statuses: `PENDING`, `IN_PROGRESS`, `COMPLETED`, `FAILED`, `SKIPPED`

---

## Get Video Info

```
GET /api/videos/{videoId}
```

**Response:**
```json
{
  "status": "ok",
  "data": {
    "videoId": "uuid",
    "sourceUrl": "https://www.youtube.com/watch?v=...",
    "title": "Video Title",
    "duration": 1234,
    "importedAt": "2026-04-16T22:00:00",
    "filePath": "data/raw/{videoId}.mp4"
  }
}
```

---

## List Clips

Get all scored clips for a video, sorted by score descending.

```
GET /api/videos/{videoId}/clips
```

**Response:**
```json
{
  "status": "ok",
  "data": {
    "videoId": "uuid",
    "clips": [
      {
        "clipId": "uuid",
        "videoId": "uuid",
        "rank": 1,
        "score": 0.87,
        "tier": "PRIMARY",
        "startTime": 45.2,
        "endTime": 72.8,
        "duration": 27.6,
        "text": "Ternyata ada rahasia yang tidak banyak orang tahu...",
        "renderStatus": "COMPLETED",
        "exportStatus": "COMPLETED",
        "previewUrl": "/api/clips/{clipId}/preview",
        "exportUrl": "/api/clips/{clipId}/export"
      }
    ]
  }
}
```

Tier values: `PRIMARY` (≥0.80), `BACKUP` (0.65-0.79), `SKIP` (<0.65)

---

## Get Clip Detail

```
GET /api/clips/{clipId}
```

**Response:**
```json
{
  "status": "ok",
  "data": {
    "clipId": "uuid",
    "videoId": "uuid",
    "rank": 1,
    "score": 0.87,
    "scoreBreakdown": {
      "hookStrength": 0.9,
      "keywordTrigger": 0.8,
      "novelty": 0.7,
      "clarity": 0.85,
      "emotionalEnergy": 0.8,
      "pauseStructure": 0.6,
      "facePresence": 0.9,
      "sceneChange": 0.5,
      "topicFit": 0.85,
      "historyScore": 0.7,
      "boosts": 0.1,
      "penalties": 0.0
    },
    "tier": "PRIMARY",
    "startTime": 45.2,
    "endTime": 72.8,
    "duration": 27.6,
    "text": "Ternyata ada rahasia yang tidak banyak orang tahu...",
    "renderStatus": "COMPLETED",
    "exportStatus": "COMPLETED",
    "previewUrl": "/api/clips/{clipId}/preview",
    "exportUrl": "/api/clips/{clipId}/export"
  }
}
```

---

## Render Clip

Render a specific clip to vertical format (no subtitles yet).

```
POST /api/clips/{clipId}/render
```

**Request:**
```json
{
  "cropMode": "CENTER"
}
```

`cropMode` options: `CENTER` (default), `FACE_TRACK` (future)

**Response:**
```json
{
  "status": "ok",
  "data": {
    "clipId": "uuid",
    "renderStatus": "COMPLETED",
    "previewUrl": "/api/clips/{clipId}/preview"
  }
}
```

---

## Export Clip

Burn subtitles and create final export file.

```
POST /api/clips/{clipId}/export
```

**Request:**
```json
{
  "subtitleStyle": "DEFAULT",
  "includeSubtitle": true
}
```

`subtitleStyle` options: `DEFAULT`, `BOLD`, `KARAOKE` (future)

**Response:**
```json
{
  "status": "ok",
  "data": {
    "clipId": "uuid",
    "exportStatus": "COMPLETED",
    "exportUrl": "/api/clips/{clipId}/export",
    "filePath": "data/exports/{clipId}.mp4"
  }
}
```

---

## Stream Preview

Stream the rendered clip for browser video player.

```
GET /api/clips/{clipId}/preview
```

Returns `video/mp4` stream (no JSON envelope). Supports range requests.

---

## Download Export

Download the final exported clip file.

```
GET /api/clips/{clipId}/export
```

Returns `video/mp4` file download with `Content-Disposition: attachment`.

---

## List Jobs

```
GET /api/jobs
```

Optional query params: `?status=RUNNING&page=0&size=20`

**Response:**
```json
{
  "status": "ok",
  "data": {
    "jobs": [
      { "jobId": "uuid", "videoId": "uuid", "status": "COMPLETED", "currentStage": "SUBTITLE" }
    ],
    "page": 0,
    "size": 20,
    "total": 1
  }
}
```

---

## Retry Failed Job

```
POST /api/jobs/{jobId}/retry
```

Restarts the pipeline from the failed stage.

**Response:**
```json
{
  "status": "ok",
  "data": {
    "jobId": "uuid",
    "status": "RUNNING",
    "currentStage": "TRANSCRIBING"
  }
}
```

---

## Error Responses

All errors use the same format:

```json
{
  "status": "error",
  "error": "Human-readable error message"
}
```

HTTP status codes:
- `200` — success
- `400` — bad request (missing params, invalid URL)
- `404` — resource not found
- `409` — conflict (e.g., video already processing)
- `500` — internal error (pipeline failure, subprocess crash)
