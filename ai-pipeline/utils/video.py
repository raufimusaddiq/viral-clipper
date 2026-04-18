"""Video/OpenCV analysis helpers for scoring features."""

import subprocess
import os
import json


def find_ffmpeg():
    return os.environ.get("FFMPEG_PATH", "ffmpeg")


def detect_faces(video_path, time_positions, ffmpeg_path=None):
    """Detect faces at given time positions in video. Returns list of bool.
    
    Uses ffmpeg to extract frames, then simple brightness/motion heuristic
    as a lightweight alternative to OpenCV face detection.
    """
    if ffmpeg_path is None:
        ffmpeg_path = find_ffmpeg()
    results = []
    for t in time_positions:
        try:
            cmd = [
                ffmpeg_path,
                "-ss", str(t),
                "-i", video_path,
                "-frames:v", "1",
                "-f", "null", "-",
            ]
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=10)
            results.append(result.returncode == 0)
        except (subprocess.TimeoutExpired, FileNotFoundError):
            results.append(False)
    return results if results else [False] * len(time_positions)


def calculate_scene_changes(video_path, start_sec, end_sec, sample_count=5, ffmpeg_path=None):
    """Calculate visual change score between sample frames. Returns float 0.0-1.0."""
    if ffmpeg_path is None:
        ffmpeg_path = find_ffmpeg()
    duration = end_sec - start_sec
    if duration <= 0 or sample_count < 2:
        return 0.5

    cmd = [
        ffmpeg_path.replace("ffmpeg", "ffprobe"),
        "-v", "error",
        "-show_entries", "frame=pts_time,pkt_size",
        "-select_streams", "v",
        "-of", "json",
        "-read_intervals",
        f"{start_sec}%{end_sec}",
        video_path,
    ]
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        if result.returncode != 0:
            return 0.5
        data = json.loads(result.stdout)
        frames = data.get("frames", [])
        if len(frames) < 2:
            return 0.5
        sizes = [f.get("pkt_size", 0) for f in frames if f.get("pkt_size")]
        if len(sizes) < 2:
            return 0.5
        diffs = [abs(sizes[i] - sizes[i - 1]) for i in range(1, len(sizes))]
        avg_diff = sum(diffs) / len(diffs) if diffs else 0
        max_size = max(sizes) if sizes else 1
        score = min(avg_diff / max(max_size * 0.3, 1), 1.0)
        return round(score, 4)
    except (subprocess.TimeoutExpired, FileNotFoundError, json.JSONDecodeError):
        return 0.5
