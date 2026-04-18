# Python Pipeline — CLI Interface Contract

## General Protocol

All Python scripts are called by Spring Boot via `ProcessBuilder`.

**Calling convention:**
```bash
python ai-pipeline/<script>.py --arg1 value1 --arg2 value2 < data/input.json
```

**Rules:**
- Input data (JSON) is passed on **stdin**
- Output data (JSON) is written to **stdout**
- Errors and logs go to **stderr** (never stdout)
- Exit code **0** = success, **non-zero** = failure
- All file paths are absolute or relative to project root
- The backend captures stdout and parses it as JSON

**Stdout JSON envelope:**
```json
{
  "success": true,
  "data": { ... }
}
```

**Error envelope (written to stdout on failure before exit):**
```json
{
  "success": false,
  "error": "Description of what went wrong"
}
```

---

## Scripts

### `transcribe.py`

Transcribes audio file to timestamped segments using faster-whisper with CUDA.

**Invocation:**
```bash
python ai-pipeline/transcribe.py \
  --audio "data/audio/{videoId}.wav" \
  --language "id" \
  --model "large-v3" \
  --device "cuda"
```

**Stdin:** (empty — all params via CLI args)

**Stdout:**
```json
{
  "success": true,
  "data": {
    "videoId": "uuid",
    "language": "id",
    "model": "large-v3",
    "segments": [
      {
        "index": 0,
        "start": 0.0,
        "end": 3.2,
        "text": "Halo semuanya",
        "confidence": 0.95
      },
      {
        "index": 1,
        "start": 3.5,
        "end": 7.8,
        "text": "hari ini kita mau bahas sesuatu yang penting",
        "confidence": 0.91
      }
    ]
  }
}
```

**Output file:** `data/transcripts/{videoId}.json` (same content as stdout data)

**Args:**

| Arg | Required | Default | Description |
|-----|----------|---------|-------------|
| `--audio` | Yes | | Path to WAV audio file |
| `--output` | No | auto-derived | Path to write transcript JSON |
| `--language` | No | `id` | Language code for Whisper |
| `--model` | No | `medium` | Whisper model size |
| `--device` | No | `cuda` | `cuda` or `cpu` |

---

### `segment.py`

Finds candidate clip segments from a transcript.

**Invocation:**
```bash
python ai-pipeline/segment.py \
  --transcript "data/transcripts/{videoId}.json" \
  --min-duration 10 \
  --max-duration 60
```

**Stdin:** (empty — reads transcript file from `--transcript` arg)

**Stdout:**
```json
{
  "success": true,
  "data": {
    "videoId": "uuid",
    "segmentCount": 8,
    "segments": [
      {
        "index": 0,
        "startTime": 45.2,
        "endTime": 72.8,
        "duration": 27.6,
        "text": "Ternyata ada rahasia yang tidak banyak orang tahu tentang ini",
        "reason": "strong opening hook + topic shift"
      }
    ]
  }
}
```

**Output file:** `data/segments/{videoId}.json`

**Args:**

| Arg | Required | Default | Description |
|-----|----------|---------|-------------|
| `--transcript` | Yes | | Path to transcript JSON |
| `--output` | No | auto-derived | Path to write segments JSON |
| `--min-duration` | No | 10 | Minimum segment duration (seconds) |
| `--max-duration` | No | 60 | Maximum segment duration (seconds) |

**Segment logic (MVP):**
1. Find pause gaps > 1.5s in transcript → potential segment boundaries
2. Merge adjacent segments that are too short (< min-duration)
3. Split segments that are too long (> max-duration) at the nearest pause
4. Prefer segments that start with a strong sentence (question, exclamation, hook phrase)
5. Assign `reason` string explaining why this segment was selected

---

### `score.py`

Scores each segment using the weighted formula. Reads segments, adds scores, writes back.

**Invocation:**
```bash
python ai-pipeline/score.py \
  --segments "data/segments/{videoId}.json" \
  --video "data/raw/{videoId}.mp4"
```

**Stdin:** (empty)

**Stdout:**
```json
{
  "success": true,
  "data": {
    "videoId": "uuid",
    "scoredSegments": [
      {
        "index": 0,
        "startTime": 45.2,
        "endTime": 72.8,
        "duration": 27.6,
        "text": "Ternyata ada rahasia yang tidak banyak orang tahu...",
        "scores": {
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
          "boostTotal": 0.1,
          "penaltyTotal": 0.0
        },
        "finalScore": 0.87,
        "tier": "PRIMARY"
      }
    ]
  }
}
```

**Output file:** updates `data/segments/{videoId}.json` in place (adds score fields)

**Args:**

| Arg | Required | Default | Description |
|-----|----------|---------|-------------|
| `--segments` | Yes | | Path to segments JSON |
| `--video` | Yes | | Path to raw video file (for face/scene analysis) |

**Scoring logic:** See `docs/scoring-spec.md` for full formula and feature definitions.

---

## Python Environment

### `requirements.txt`

```
faster-whisper>=1.0
opencv-python>=4.8
numpy>=1.24
```

### Virtual environment

Scripts expect to run inside the project's venv:

```bash
cd ai-pipeline
python -m venv .venv
.venv\Scripts\activate      # Windows
pip install -r requirements.txt
```

The Spring Boot backend should call scripts using the venv Python:

```
ai-pipeline/.venv/Scripts/python.exe ai-pipeline/transcribe.py ...
```

Configure the Python path via `PYTHON_PATH` env var in `.env`.

---

## Adding a New Script

1. Create `ai-pipeline/<name>.py`
2. Use `argparse` for CLI args
3. Read input from file paths in args (or stdin if needed)
4. Write JSON result to stdout using the envelope
5. Log to stderr only
6. Exit 0 on success, 1 on failure
7. Write output file if applicable
8. Update this doc with the new script's interface
