#!/usr/bin/env python3
"""Burn word-level subtitles into rendered clips using ffmpeg drawtext."""

import argparse
import json
import os
import subprocess
import sys


def find_ffmpeg():
    return os.environ.get("FFMPEG_PATH", "ffmpeg")


def _detect_nvenc(ffmpeg_path):
    try:
        result = subprocess.run(
            [ffmpeg_path, "-encoders"], capture_output=True, text=True, timeout=5
        )
        return "h264_nvenc" in result.stdout
    except Exception:
        return False


def escape_drawtext(text):
    return (
        text.replace("'", "'\\''")
        .replace(":", "\\:")
        .replace(",", "\\,")
        .replace("[", "\\[")
        .replace("]", "\\]")
        .replace("%", "%%")
    )


def build_word_timeline(segments, clip_start, clip_end):
    words = []
    for seg in segments:
        seg_start = seg.get("start", 0.0)
        seg_end = seg.get("end", 0.0)
        if seg_end < clip_start or seg_start > clip_end:
            continue
        text = seg.get("text", "").strip()
        if not text:
            continue
        duration = seg_end - seg_start
        word_list = text.split()
        if not word_list:
            continue
        word_duration = duration / len(word_list)
        for i, word in enumerate(word_list):
            w_start = seg_start + (i * word_duration)
            w_end = seg_start + ((i + 1) * word_duration)
            if w_end > clip_end or w_start < clip_start:
                continue
            words.append({
                "text": word,
                "start": round(w_start - clip_start, 3),
                "end": round(w_end - clip_start, 3),
            })
    return words


def build_subtitle_filter(words, max_chars=3, fontsize=52):
    if not words:
        return "null"
    phrases = []
    chunk = []
    chunk_start = words[0]["start"]
    for w in words:
        chunk.append(w["text"])
        if len(chunk) >= max_chars:
            text = escape_drawtext(" ".join(chunk))
            t_start = chunk_start
            t_end = w["end"]
            phrases.append((text, t_start, t_end))
            chunk = []
            chunk_start = w["end"]
    if chunk:
        text = escape_drawtext(" ".join(chunk))
        t_start = chunk_start
        t_end = words[-1]["end"]
        phrases.append((text, t_start, t_end))

    filters = []
    for text, t_start, t_end in phrases:
        filters.append(
            f"drawtext=text='{text}':"
            f"fontsize={fontsize}:fontcolor=yellow:"
            f"borderw=4:bordercolor=black:"
            f"shadowcolor=black@0.5:shadowx=2:shadowy=2:"
            f"x=(w-text_w)/2:y=h*0.70:"
            f"enable='between(t,{t_start:.3f},{t_end:.3f})'"
        )
    return ",".join(filters)


def burn_subtitles(render_path, subtitle_filter, output_path, ffmpeg_path=None):
    if ffmpeg_path is None:
        ffmpeg_path = find_ffmpeg()
    os.makedirs(os.path.dirname(output_path), exist_ok=True)

    use_nvenc = _detect_nvenc(ffmpeg_path)

    cmd = [
        ffmpeg_path,
        "-i", render_path,
        "-vf", subtitle_filter,
        "-c:v", "h264_nvenc" if use_nvenc else "libx264",
    ]

    if use_nvenc:
        cmd.extend(["-preset", "p4", "-rc", "vbr", "-cq", "23"])
    else:
        cmd.extend(["-preset", "fast", "-crf", "23"])

    cmd.extend(["-c:a", "copy", "-y", output_path])

    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(f"ffmpeg subtitle burn failed: {result.stderr[-500:]}")
    return output_path


def main():
    parser = argparse.ArgumentParser(description="Burn subtitles into rendered clips")
    parser.add_argument("--transcript", required=True, help="Path to transcript JSON")
    parser.add_argument("--segments", required=True, help="Path to scored segments JSON")
    parser.add_argument("--render-dir", required=True, help="Directory with rendered clips")
    parser.add_argument("--output-dir", required=True, help="Directory for exported clips with subtitles")
    parser.add_argument("--tiers", default="PRIMARY,BACKUP", help="Comma-separated tiers to subtitle")
    args = parser.parse_args()

    try:
        with open(args.transcript, "r", encoding="utf-8") as f:
            transcript = json.load(f)
        with open(args.segments, "r", encoding="utf-8") as f:
            segments_data = json.load(f)

        tiers = [t.strip() for t in args.tiers.split(",")]
        word_segments = transcript.get("segments", [])
        scored_segments = segments_data.get("scoredSegments", [])
        to_subtitle = [s for s in scored_segments if s.get("tier") in tiers]

        os.makedirs(args.output_dir, exist_ok=True)
        exported = []

        for seg in to_subtitle:
            clip_id = seg.get("rank", seg.get("index", 0))
            render_path = os.path.join(args.render_dir, f"clip_{clip_id}.mp4")
            output_path = os.path.join(args.output_dir, f"clip_{clip_id}_subtitled.mp4")

            if not os.path.exists(render_path):
                exported.append({
                    "rank": clip_id,
                    "status": "SKIPPED",
                    "error": "render not found",
                })
                continue

            words = build_word_timeline(
                word_segments, seg["startTime"], seg["endTime"]
            )
            subtitle_filter = build_subtitle_filter(words)

            try:
                burn_subtitles(render_path, subtitle_filter, output_path)
                exported.append({
                    "rank": clip_id,
                    "path": output_path,
                    "status": "COMPLETED",
                    "wordCount": len(words),
                })
            except Exception as e:
                exported.append({
                    "rank": clip_id,
                    "path": output_path,
                    "status": "FAILED",
                    "error": str(e),
                })

        result = {
            "videoId": segments_data.get("videoId", ""),
            "exportedCount": len([e for e in exported if e["status"] == "COMPLETED"]),
            "failedCount": len([e for e in exported if e["status"] == "FAILED"]),
            "clips": exported,
        }

        output = {"success": True, "data": result}
        print(json.dumps(output, ensure_ascii=False))

    except Exception as e:
        error_output = {"success": False, "error": str(e)}
        print(json.dumps(error_output, ensure_ascii=False), file=sys.stdout)
        print(f"ERROR: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
