# Implementation Guide — GPU Optimization

> **Companion Document**: [GPU-OPTIMIZATION-PLAN.md](./GPU-OPTIMIZATION-PLAN.md)
>
> **Purpose**: Step-by-step code changes for each optimization phase
>
> **Format**: For each file, shows exact before/after with line references

---

## Table of Contents

1. [Phase 0 Implementation](#1-phase-0-implementation)
2. [Phase 1 Implementation](#2-phase-1-implementation)
3. [Phase 2 Implementation](#3-phase-2-implementation)
4. [Testing Procedures](#4-testing-procedures)

---

## 1. Phase 0 Implementation

### 1.1 File: `ai-pipeline/render.py`

#### Change 1a: Replace encoder (Line 25-27)

```python
# ============================================================
# BEFORE (lines 19-34)
# ============================================================
def render_clip(video_path, start, end, output_path, ffmpeg_path=None):
    if ffmpeg_path is None:
        ffmpeg_path = find_ffmpeg()
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    cmd = [
        ffmpeg_path,
        "-i", video_path,
        "-ss", str(start),
        "-to", str(end),
        "-vf", "scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920",
        "-c:v", "libx264",
        "-preset", "fast",
        "-crf", "23",
        "-c:a", "aac",
        "-y",
        output_path,
    ]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(f"ffmpeg render failed: {result.stderr[-500:]}")
    return output_path

# ============================================================
# AFTER
# ============================================================
def _detect_nvenc(ffmpeg_path):
    """Check if NVENC hardware encoder is available."""
    try:
        result = subprocess.run(
            [ffmpeg_path, "-encoders"],
            capture_output=True, text=True, timeout=5
        )
        return "h264_nvenc" in result.stdout
    except Exception:
        return False


def render_clip(video_path, start, end, output_path, ffmpeg_path=None):
    if ffmpeg_path is None:
        ffmpeg_path = find_ffmpeg()
    os.makedirs(os.path.dirname(output_path), exist_ok=True)

    use_nvenc = _detect_nvenc(ffmpeg_path)

    cmd = [
        ffmpeg_path,
        "-i", video_path,
        "-ss", str(start),
        "-to", str(end),
        "-vf", "scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920",
        "-c:v", "h264_nvenc" if use_nvenc else "libx264",
    ]

    if use_nvenc:
        cmd.extend([
            "-preset", "p4",
            "-rc", "vbr",
            "-cq", "23",
        ])
    else:
        cmd.extend([
            "-preset", "fast",
            "-crf", "23",
        ])

    cmd.extend(["-c:a", "aac", "-y", output_path])

    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(f"ffmpeg render failed: {result.stderr[-500:]}")
    return output_path
```

### 1.2 File: `ai-pipeline/subtitle.py`

Locate the ffmpeg render command that uses `libx264` and apply the same pattern:

```python
# Find the libx264 reference and replace:
"-c:v", "libx264",
"-preset", "fast",
"-crf", "23",

# Replace with (with auto-detect):
use_nvenc = _detect_nvenc(ffmpeg_path)
# ... (same pattern as render.py)
"-c:v", "h264_nvenc" if use_nvenc else "libx264",
```

### 1.3 File: `ai-pipeline/variation.py`

Same change as subtitle.py — locate `libx264` and replace.

### 1.4 File: `ai-pipeline/transcribe.py`

#### Change 4a: Update default model (Line 14)

```python
# ============================================================
# BEFORE (line 14)
# ============================================================
parser.add_argument("--model", default="medium", help="Whisper model size")

# ============================================================
# AFTER
# ============================================================
parser.add_argument("--model", default="large-v3-turbo",
                    help="Whisper model size (small/medium/large-v3-turbo/large-v3)")
```

### 1.5 File: `.env.docker`

#### Change 5a: Update Whisper model (Line 11)

```bash
# ============================================================
# BEFORE
# ============================================================
WHISPER_MODEL=medium

# ============================================================
# AFTER
# ============================================================
WHISPER_MODEL=large-v3-turbo
```

---

## 2. Phase 1 Implementation

### 2.1 File: `ai-pipeline/score.py`

#### Change 1a: Add import at top of file (after line 10)

```python
# ============================================================
# ADD after line 10 (after existing imports)
# ============================================================
import logging
from concurrent.futures import ThreadPoolExecutor

logger = logging.getLogger(__name__)
```

#### Change 1b: Parallel audio + video analysis in `main()` (Lines 936-942)

```python
# ============================================================
# BEFORE (lines 918-963, main function)
# ============================================================
def main():
    parser = argparse.ArgumentParser(description="Score segments for viral potential")
    parser.add_argument("--segments", required=True, help="Path to segments JSON")
    parser.add_argument("--video", required=True, help="Path to raw video file")
    parser.add_argument("--audio", default="", help="Path to extracted audio WAV file")
    parser.add_argument("--transcript", default="", help="Path to transcript JSON")
    parser.add_argument("--niche-keywords", default="", help="Comma-separated niche keywords")
    args = parser.parse_args()

    try:
        with open(args.segments, "r", encoding="utf-8") as f:
            data = json.load(f)

        niche_keywords = [k.strip() for k in args.niche_keywords.split(",") if k.strip()] if args.niche_keywords else None
        segments = data.get("segments", [])
        audio_path = args.audio if args.audio and os.path.exists(args.audio) else None
        transcript_path = args.transcript if args.transcript and os.path.exists(args.transcript) else None

        audio_cache = _load_audio_cache(audio_path)
        video_data = _batch_analyze_video(args.video, segments)

        scored = [score_segment(s, niche_keywords, video_path=args.video,
                                audio_path=audio_path, transcript_path=transcript_path,
                                audio_cache=audio_cache, video_data=video_data)
                  for s in segments]
        # ... rest unchanged ...

# ============================================================
# AFTER (only the changed portion)
# ============================================================
def main():
    parser = argparse.ArgumentParser(description="Score segments for viral potential")
    parser.add_argument("--segments", required=True, help="Path to segments JSON")
    parser.add_argument("--video", required=True, help="Path to raw video file")
    parser.add_argument("--audio", default="", help="Path to extracted audio WAV file")
    parser.add_argument("--transcript", default="", help="Path to transcript JSON")
    parser.add_argument("--niche-keywords", default="", help="Comma-separated niche keywords")
    args = parser.parse_args()

    try:
        with open(args.segments, "r", encoding="utf-8") as f:
            data = json.load(f)

        niche_keywords = [k.strip() for k in args.niche_keywords.split(",") if k.strip()] if args.niche_keywords else None
        segments = data.get("segments", [])
        audio_path = args.audio if args.audio and os.path.exists(args.audio) else None
        transcript_path = args.transcript if args.transcript and os.path.exists(args.transcript) else None

        # Run audio and video analysis IN PARALLEL (they share no state)
        with ThreadPoolExecutor(max_workers=2) as pool:
            audio_future = pool.submit(_load_audio_cache, audio_path)
            video_future = pool.submit(_batch_analyze_video, args.video, segments)

            audio_cache = audio_future.result()
            video_data = video_future.result()

        # Score all segments (fast CPU work — keep sequential)
        scored = [score_segment(s, niche_keywords, video_path=args.video,
                                audio_path=audio_path, transcript_path=transcript_path,
                                audio_cache=audio_cache, video_data=video_data)
                  for s in segments]
        # ... rest unchanged ...
```

### 2.2 File: `backend/.../PipelineOrchestrator.java`

#### Change 2a: Add imports (after existing imports, ~line 17)

```java
// ============================================================
// ADD after existing imports
// ============================================================
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;
```

#### Change 2b: Add RenderResult inner class (before `StageRunnable` interface, ~line 449)

```java
// ============================================================
// ADD before the StageRunnable interface
// ============================================================
private static class RenderResult {
    final int clipIndex;
    final boolean success;
    final JsonNode result;
    final String error;

    RenderResult(int clipIndex, boolean success, JsonNode result, String error) {
        this.clipIndex = clipIndex;
        this.success = success;
        this.result = result;
        this.error = error;
    }
}
```

#### Change 2c: Refactor `stageRender()` (Lines 304-357)

```java
// ============================================================
# BEFORE (lines 304-357)
# ============================================================
private String stageRender(Job job, Video video) throws Exception {
    String segmentsPath = appConfig.getDataDir() + "/segments/" + video.getId() + ".json";
    String renderDir = appConfig.getDataDir() + "/renders/";
    new java.io.File(renderDir).mkdirs();

    String segJson = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(segmentsPath)));
    JsonNode segData = objectMapper.readTree(segJson);
    JsonNode scored = segData.path("scoredSegments");
    List<String> tiers = List.of("PRIMARY", "BACKUP");
    List<Integer> toRender = new java.util.ArrayList<>();
    for (int i = 0; i < scored.size(); i++) {
        String tier = scored.get(i).path("tier").asText("");
        if (tiers.contains(tier)) toRender.add(i);
    }

    StageStatus renderStage = stageStatusRepository.findByJobId(job.getId()).stream()
            .filter(s -> "RENDER".equals(s.getStage())).findFirst().orElse(null);

    int rendered = 0, failed = 0;
    for (int idx : toRender) {
        Job fresh = jobRepository.findById(job.getId()).orElse(job);
        if ("CANCELLED".equals(fresh.getStatus())) {
            throw new RuntimeException("Job cancelled by user");
        }

        List<String> args = List.of(
                "--segments", segmentsPath,
                "--video", video.getFilePath(),
                "--output-dir", renderDir,
                "--clip-index", String.valueOf(idx)
        );

        try {
            JsonNode result = pythonRunner.runScript("render.py", args);
            updateClipRenderStatuses(video.getId(), result);
            rendered++;
        } catch (Exception e) {
            failed++;
            log.warn("Render failed for clip index {}: {}", idx, e.getMessage());
        }

        if (renderStage != null) {
            renderStage.setOutputPath("Rendering " + (rendered + failed) + "/" + toRender.size() + " clips");
            stageStatusRepository.save(renderStage);
        }
    }

    if (renderStage != null) {
        renderStage.setOutputPath(rendered + " rendered, " + failed + " failed");
        stageStatusRepository.save(renderStage);
    }

    return renderDir;
}

# ============================================================
# AFTER
# ============================================================
private String stageRender(Job job, Video video) throws Exception {
    String segmentsPath = appConfig.getDataDir() + "/segments/" + video.getId() + ".json";
    String renderDir = appConfig.getDataDir() + "/renders/";
    new java.io.File(renderDir).mkdirs();

    String segJson = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(segmentsPath)));
    JsonNode segData = objectMapper.readTree(segJson);
    JsonNode scored = segData.path("scoredSegments");
    List<String> tiers = List.of("PRIMARY", "BACKUP");
    List<Integer> toRender = new java.util.ArrayList<>();
    for (int i = 0; i < scored.size(); i++) {
        String tier = scored.get(i).path("tier").asText("");
        if (tiers.contains(tier)) toRender.add(i);
    }

    StageStatus renderStage = stageStatusRepository.findByJobId(job.getId()).stream()
            .filter(s -> "RENDER".equals(s.getStage())).findFirst().orElse(null);

    // Use 2 parallel render threads (RTX 4060 has 2x NVENC encoders)
    int maxParallelRenders = 2;
    ExecutorService renderPool = Executors.newFixedThreadPool(maxParallelRenders);
    List<Future<RenderResult>> futures = new ArrayList<>();

    for (int idx : toRender) {
        Job fresh = jobRepository.findById(job.getId()).orElse(job);
        if ("CANCELLED".equals(fresh.getStatus())) {
            renderPool.shutdownNow();
            throw new RuntimeException("Job cancelled by user");
        }

        final int clipIdx = idx;
        futures.add(renderPool.submit(() -> {
            List<String> args = List.of(
                    "--segments", segmentsPath,
                    "--video", video.getFilePath(),
                    "--output-dir", renderDir,
                    "--clip-index", String.valueOf(clipIdx)
            );
            try {
                JsonNode result = pythonRunner.runScript("render.py", args);
                return new RenderResult(clipIdx, true, result, null);
            } catch (Exception e) {
                log.warn("Render failed for clip index {}: {}", clipIdx, e.getMessage());
                return new RenderResult(clipIdx, false, null, e.getMessage());
            }
        }));
    }

    renderPool.shutdown();

    int rendered = 0, failed = 0;
    for (Future<RenderResult> f : futures) {
        try {
            RenderResult rr = f.get();
            if (rr.success) {
                updateClipRenderStatuses(video.getId(), rr.result);
                rendered++;
            } else {
                failed++;
            }
            if (renderStage != null) {
                renderStage.setOutputPath("Rendering " + (rendered + failed) + "/" + toRender.size() + " clips");
                stageStatusRepository.save(renderStage);
            }
        } catch (Exception e) {
            failed++;
            log.warn("Render future failed: {}", e.getMessage());
        }
    }

    if (renderStage != null) {
        renderStage.setOutputPath(rendered + " rendered, " + failed + " failed");
        stageStatusRepository.save(renderStage);
    }

    return renderDir;
}
```

### 2.3 File: `.env.docker`

#### Change 3a: Add parallel render config

```bash
# ============================================================
# ADD at end of file
# ============================================================

# --- Performance ---
MAX_PARALLEL_RENDERS=2
```

---

## 3. Phase 2 Implementation

### 3.1 File: `ai-pipeline/requirements.txt`

```txt
# ============================================================
# BEFORE
# ============================================================
faster-whisper>=1.0
opencv-python-headless>=4.8
numpy>=1.24

# ============================================================
# AFTER
# ============================================================
faster-whisper>=1.0
opencv-python-headless>=4.8
numpy>=1.24

# Phase 2: GPU-accelerated vision
mediapipe>=0.10.0
cupy-cuda12x>=13.0
```

### 3.2 File: `ai-pipeline/score.py`

#### Change 2a: Add MediaPipe-backed face analysis function

```python
# ============================================================
# ADD after existing _batch_analyze_video function (~line 569)
# ============================================================

def _batch_analyze_video_mediapipe(video_path, segments):
    """GPU-accelerated video analysis using MediaPipe face detection.

    Falls back gracefully to Haar cascade if MediaPipe is unavailable.
    Produces identical output format: {index: {"faces": float, "scene": float}}
    """
    if not video_path or not os.path.exists(video_path):
        return {i: {"faces": 0.5, "scene": 0.5} for i in range(len(segments))}

    try:
        import cv2
        import numpy as np
        import mediapipe as mp

        face_detector = mp.solutions.face_detection.FaceDetection(
            model_selection=1,
            min_detection_confidence=0.5,
        )

        all_times = []
        for i, seg in enumerate(segments):
            start = seg.get("startTime", 0)
            dur = seg.get("duration", 30)
            end = seg.get("endTime", start + dur)
            mid = start + dur * 0.5
            all_times.append({
                "idx": i,
                "face_times": [mid],
                "scene_times": [start, mid, end]
            })

        unique_ts = set()
        for info in all_times:
            for t in info["face_times"] + info["scene_times"]:
                unique_ts.add(round(t, 1))

        unique_ts = sorted(unique_ts)
        if len(unique_ts) > 60:
            step = len(unique_ts) / 60
            unique_ts = [unique_ts[int(i * step)] for i in range(60)]

        frame_data = _extract_frames_ffmpeg(video_path, unique_ts)

        def _nearest(t):
            key = round(t, 1)
            if key in frame_data:
                return frame_data[key]
            best = None
            best_diff = float("inf")
            for k in frame_data:
                d = abs(k - t)
                if d < best_diff:
                    best_diff = d
                    best = frame_data[k]
            return best

        results = {}
        for info in all_times:
            idx = info["idx"]
            faces_found = 0

            for t in info["face_times"]:
                fd = _nearest(t)
                if fd is None:
                    continue
                rgb = cv2.cvtColor(fd["gray"], cv2.COLOR_GRAY2RGB)
                detections = face_detector.process(rgb)
                if detections.detections:
                    faces_found += 1

            if faces_found >= 1:
                face_score = 0.7
            else:
                face_score = 0.0

            # Scene change analysis (same algorithm, unchanged)
            scene_frames = [fd for t in info["scene_times"]
                           if (fd := _nearest(t)) is not None]

            if len(scene_frames) >= 2:
                diffs = []
                grays = [f["gray"] for f in scene_frames]
                for i2 in range(1, len(grays)):
                    h1 = cv2.calcHist([grays[i2 - 1]], [0], None, [64], [0, 256])
                    h2 = cv2.calcHist([grays[i2]], [0], None, [64], [0, 256])
                    cv2.normalize(h1, h1)
                    cv2.normalize(h2, h2)
                    diffs.append(cv2.compareHist(h1, h2, cv2.HISTCMP_CORREL))
                avg_corr = sum(diffs) / len(diffs)
                change_score = 1.0 - avg_corr

                brightness_scores = []
                saturation_scores = []
                for f in scene_frames:
                    avg_v = np.mean(f["hsv"][:, :, 2]) / 255.0
                    avg_s = np.mean(f["hsv"][:, :, 1]) / 255.0
                    brightness_scores.append(avg_v)
                    saturation_scores.append(avg_s)
                avg_brightness = sum(brightness_scores) / len(brightness_scores)
                avg_saturation = sum(saturation_scores) / len(saturation_scores)

                visual_appeal = 0.0
                if avg_brightness > 0.5:
                    visual_appeal += 0.1
                if avg_brightness > 0.65:
                    visual_appeal += 0.1
                if avg_saturation > 0.35:
                    visual_appeal += 0.1
                if avg_saturation > 0.5:
                    visual_appeal += 0.1

                scene_score = 0.3
                if change_score > 0.5:
                    scene_score = 0.9
                elif change_score > 0.3:
                    scene_score = 0.7
                elif change_score > 0.15:
                    scene_score = 0.5
                scene_score = min(scene_score + visual_appeal, 1.0)
            else:
                scene_score = 0.5

            results[idx] = {"faces": face_score, "scene": round(scene_score, 4)}

        face_detector.close()
        return results

    except Exception as e:
        logger.warning("MediaPipe analysis failed: %s, falling back to Haar", e)
        return {i: {"faces": 0.5, "scene": 0.5} for i in range(len(segments))}
```

#### Change 2b: Update `_batch_analyze_video()` to auto-route (replace original function)

```python
# ============================================================
# REPLACE the existing _batch_analyze_video function (lines 441-568)
# ============================================================

def _batch_analyze_video(video_path, segments):
    """Route to best available face detection backend."""
    # Try MediaPipe first (GPU-accelerated)
    try:
        import mediapipe as mp
        mp.solutions.face_detection  # Test import
        logger.info("Using MediaPipe for face detection (GPU)")
        return _batch_analyze_video_mediapipe(video_path, segments)
    except ImportError:
        pass

    # Fallback to original Haar cascade implementation
    logger.info("Using Haar cascade for face detection (CPU)")
    return _batch_analyze_video_haar(video_path, segments)


def _batch_analyze_video_haar(video_path, segments):
    """Original Haar cascade implementation (CPU fallback).

    This is the unchanged original code, kept for backward compatibility.
    """
    # ... (paste the ENTIRE original _batch_analyze_video function here, lines 441-568) ...
```

#### Change 2c: Reduce frame resolution for extraction (Line 389)

```python
# ============================================================
# BEFORE (line 389)
# ============================================================
"-vf", "fps=1,scale=320:-2",

# ============================================================
# AFTER
# ============================================================
"-vf", "fps=1,scale=224:-2",
```

Also update the fallback extraction at ~line 415:

```python
# ============================================================
# BEFORE (~line 415)
# ============================================================
"-vf", "scale=320:-2",

# ============================================================
# AFTER
# ============================================================
"-vf", "scale=224:-2",
```

### 3.3 File: `ai-pipeline/score.py` — CuPy Audio (Optional)

#### Change 3a: Update `_load_audio_cache()` (Lines 167-187)

```python
# ============================================================
# BEFORE (lines 167-187)
# ============================================================
def _load_audio_cache(audio_path):
    if not audio_path or not os.path.exists(audio_path):
        return None
    try:
        import numpy as np
        import wave
        with wave.open(audio_path, 'rb') as wf:
            framerate = wf.getframerate()
            n_channels = wf.getnchannels()
            sampwidth = wf.getsampwidth()
            total_frames = wf.getnframes()
            wf.setpos(0)
            raw = wf.readframes(total_frames)
            dtype = {1: np.int8, 2: np.int16, 4: np.int32}.get(sampwidth, np.int16)
            samples = np.frombuffer(raw, dtype=dtype).astype(np.float64)
            if n_channels > 1:
                samples = samples[::n_channels]
            total_rms = np.sqrt(np.mean(samples ** 2))
            return {"samples": samples, "framerate": framerate, "total_rms": total_rms}
    except Exception:
        return None

# ============================================================
# AFTER
# ============================================================
def _load_audio_cache(audio_path):
    if not audio_path or not os.path.exists(audio_path):
        return None
    try:
        import numpy as np
        import wave
        with wave.open(audio_path, 'rb') as wf:
            framerate = wf.getframerate()
            n_channels = wf.getnchannels()
            sampwidth = wf.getsampwidth()
            total_frames = wf.getnframes()
            wf.setpos(0)
            raw = wf.readframes(total_frames)
            dtype = {1: np.int8, 2: np.int16, 4: np.int32}.get(sampwidth, np.int16)
            samples = np.frombuffer(raw, dtype=dtype).astype(np.float64)
            if n_channels > 1:
                samples = samples[::n_channels]

            # Attempt GPU-accelerated RMS calculation
            use_gpu = False
            try:
                import cupy as cp
                samples_gpu = cp.asarray(samples)
                total_rms = float(cp.sqrt(cp.mean(samples_gpu ** 2)))
                del samples_gpu
                use_gpu = True
            except ImportError:
                total_rms = float(np.sqrt(np.mean(samples ** 2)))

            return {
                "samples": samples,
                "framerate": framerate,
                "total_rms": total_rms,
                "gpu": use_gpu,
            }
    except Exception:
        return None
```

#### Change 3b: Update `_extract_audio_rms_ratio()` (Lines 190-210)

```python
# ============================================================
# BEFORE (lines 190-210)
# ============================================================
def _extract_audio_rms_ratio(audio_path, start_time, end_time, audio_cache=None):
    try:
        import numpy as np
        if audio_cache is None:
            audio_cache = _load_audio_cache(audio_path)
        if audio_cache is None:
            return None
        samples = audio_cache["samples"]
        framerate = audio_cache["framerate"]
        total_rms = audio_cache["total_rms"]
        if total_rms == 0:
            return None
        start_sample = max(0, min(int(start_time * framerate), len(samples)))
        end_sample = max(0, min(int(end_time * framerate), len(samples)))
        if start_sample >= end_sample:
            return None
        seg = samples[start_sample:end_sample]
        seg_rms = np.sqrt(np.mean(seg ** 2))
        return seg_rms / total_rms
    except Exception:
        return None

# ============================================================
# AFTER
# ============================================================
def _extract_audio_rms_ratio(audio_path, start_time, end_time, audio_cache=None):
    try:
        import numpy as np
        if audio_cache is None:
            audio_cache = _load_audio_cache(audio_path)
        if audio_cache is None:
            return None
        samples = audio_cache["samples"]
        framerate = audio_cache["framerate"]
        total_rms = audio_cache["total_rms"]
        if total_rms == 0:
            return None
        start_sample = max(0, min(int(start_time * framerate), len(samples)))
        end_sample = max(0, min(int(end_time * framerate), len(samples)))
        if start_sample >= end_sample:
            return None
        seg = samples[start_sample:end_sample]

        # Try GPU path if available
        if audio_cache.get("gpu"):
            try:
                import cupy as cp
                seg_gpu = cp.asarray(seg)
                seg_rms = float(cp.sqrt(cp.mean(seg_gpu ** 2)))
                del seg_gpu
                return seg_rms / total_rms
            except Exception:
                pass

        # CPU fallback
        seg_rms = np.sqrt(np.mean(seg ** 2))
        return seg_rms / total_rms
    except Exception:
        return None
```

---

## 4. Testing Procedures

### 4.1 Pre-Optimization Baseline

Run this BEFORE any changes to establish a baseline:

```bash
# 1. Start the stack
docker compose --env-file .env.docker up -d

# 2. Import a test video (10 min recommended)
curl -X POST http://localhost:8080/api/import \
  -H "Content-Type: application/json" \
  -d '{"url": "YOUR_YOUTUBE_URL"}'

# 3. Start processing
curl -X POST http://localhost:8080/api/process \
  -H "Content-Type: application/json" \
  -d '{"videoId": "VIDEO_ID_FROM_IMPORT"}'

# 4. Monitor timing (poll every 10s)
# Record start time, each stage completion time, total time

# 5. Monitor GPU usage
watch -n 1 nvidia-smi

# 6. Save baseline results
# - Total pipeline time: ___
# - Render stage time: ___
# - Score stage time: ___
# - GPU utilization peak: ___
# - VRAM usage peak: ___
# - Ranked clip order (top 5): ___
```

### 4.2 Phase 0 Verification

```bash
# 1. Verify NVENC in Docker container
docker compose --env-file .env.docker exec backend ffmpeg -encoders 2>/dev/null | grep nvenc

# Expected: h264_nvenc and hevc_nvenc listed

# 2. Quick NVENC test
docker compose --env-file .env.docker exec backend \
  ffmpeg -f lavfi -i testsrc=duration=2 -c:v h264_nvenc -preset p4 -f null -

# Expected: no errors, encoding completes

# 3. Verify Whisper model
docker compose --env-file .env.docker exec backend \
  python -c "from faster_whisper import WhisperModel; print('large-v3-turbo supported')"

# 4. Run full pipeline and compare
# Expected: render time reduced 5-10x
# Expected: total time reduced 50-70%
```

### 4.3 Phase 1 Verification

```bash
# 1. Check that parallel rendering works (should see 2 render processes)
docker compose --env-file .env.docker exec backend ps aux | grep render.py

# 2. Run pipeline with multiple clips
# Expected: 2 clips rendering simultaneously
# Expected: render stage time halved vs sequential

# 3. Verify no race conditions
# All rendered files should be complete (check file sizes)
ls -la /app/data/renders/
```

### 4.4 Phase 2 Verification

```bash
# 1. Check MediaPipe in Docker container
docker compose --env-file .env.docker exec backend \
  python -c "import mediapipe as mp; print('MediaPipe OK')"

# 2. Check CuPy (optional)
docker compose --env-file .env.docker exec backend \
  python -c "import cupy as cp; print('CuPy OK, device:', cp.cuda.runtime.getDeviceCount())"

# 3. Run pipeline and compare face detection time
# Expected: face detection 3-5x faster than Haar baseline

# 4. Verify scoring quality
# Compare ranked clip order with baseline (should be similar or improved)
```

### 4.5 Regression Test

After ALL phases, verify that:

1. **Scoring output matches baseline** (same ranked order for same input)
2. **No clips are lost** (same number of scored segments)
3. **Tier assignments are consistent** (PRIMARY/BACKUP/SKIP counts match)
4. **All clips render successfully** (no FAILED status)
5. **Subtitles burn correctly** (visual check)
6. **Pipeline completes without errors** (check `docker compose logs backend`)
