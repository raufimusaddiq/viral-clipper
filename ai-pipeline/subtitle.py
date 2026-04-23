#!/usr/bin/env python3
"""Burn word-level subtitles into rendered clips using ffmpeg drawtext.

NVENC-first with libx264 fallback, same pattern as render.py. drawtext is
CPU-only so we leave decode CPU-side (no -hwaccel) to avoid an extra GPU↔CPU
copy; the encode is GPU. ``format=yuv420p`` is appended to the filter chain
so NVENC H.264 never sees a 10-bit / 4:4:4 input.
"""

import argparse
import json
import os
import subprocess
import sys
import tempfile


_NVENC_CACHE = None


def find_ffmpeg():
    return os.environ.get("FFMPEG_PATH", "ffmpeg")


def _probe_nvenc(ffmpeg_path):
    global _NVENC_CACHE
    if _NVENC_CACHE is not None:
        return _NVENC_CACHE
    try:
        listing = subprocess.run(
            [ffmpeg_path, "-hide_banner", "-encoders"],
            capture_output=True, text=True, timeout=5,
        )
        if "h264_nvenc" not in listing.stdout:
            _NVENC_CACHE = False
            return False
        with tempfile.NamedTemporaryFile(suffix=".mp4", delete=False) as tmp:
            probe_out = tmp.name
        try:
            probe = subprocess.run(
                [
                    ffmpeg_path, "-hide_banner", "-loglevel", "error",
                    "-f", "lavfi",
                    "-i", "testsrc=size=128x128:rate=1:duration=1",
                    "-vf", "format=yuv420p",
                    "-c:v", "h264_nvenc", "-preset", "p4",
                    "-frames:v", "1", "-y", probe_out,
                ],
                capture_output=True, text=True, timeout=10,
            )
            _NVENC_CACHE = probe.returncode == 0
        finally:
            try:
                os.remove(probe_out)
            except OSError:
                pass
        return _NVENC_CACHE
    except Exception:
        _NVENC_CACHE = False
        return False


def _detect_nvenc(ffmpeg_path):
    return _probe_nvenc(ffmpeg_path)


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


def _build_burn_cmd(ffmpeg_path, render_path, subtitle_filter, output_path, use_nvenc):
    if not subtitle_filter or subtitle_filter == "null":
        vf = "format=yuv420p"
    else:
        vf = f"{subtitle_filter},format=yuv420p"
    cmd = [
        ffmpeg_path, "-hide_banner", "-loglevel", "error",
        "-i", render_path,
        "-vf", vf,
    ]
    if use_nvenc:
        cmd.extend([
            "-c:v", "h264_nvenc",
            "-preset", "p4", "-tune", "hq",
            "-rc", "vbr", "-cq", "23", "-b:v", "0",
        ])
    else:
        cmd.extend(["-c:v", "libx264", "-preset", "fast", "-crf", "23"])
    cmd.extend(["-c:a", "copy", "-y", output_path])
    return cmd


def burn_subtitles(render_path, subtitle_filter, output_path, ffmpeg_path=None):
    if ffmpeg_path is None:
        ffmpeg_path = find_ffmpeg()
    os.makedirs(os.path.dirname(output_path), exist_ok=True)

    use_nvenc = _probe_nvenc(ffmpeg_path)
    cmd = _build_burn_cmd(ffmpeg_path, render_path, subtitle_filter, output_path, use_nvenc)
    result = subprocess.run(cmd, capture_output=True, text=True)

    if result.returncode != 0 and use_nvenc:
        print(
            f"WARN: h264_nvenc failed burning {output_path}, falling back to libx264. "
            f"stderr={result.stderr[-400:]}",
            file=sys.stderr,
        )
        cmd = _build_burn_cmd(
            ffmpeg_path, render_path, subtitle_filter, output_path, use_nvenc=False,
        )
        result = subprocess.run(cmd, capture_output=True, text=True)

    if result.returncode != 0:
        raise RuntimeError(
            f"ffmpeg subtitle burn failed (cmd={' '.join(cmd)}): {result.stderr[-500:]}"
        )
    return output_path


def main():
    parser = argparse.ArgumentParser(description="Burn subtitles into rendered clips")
    parser.add_argument("--transcript", required=True, help="Path to transcript JSON")
    parser.add_argument("--segments", required=True, help="Path to scored segments JSON")
    parser.add_argument("--render-dir", required=True, help="Directory with rendered clips")
    parser.add_argument("--output-dir", required=True, help="Directory for exported clips with subtitles")
    parser.add_argument("--tiers", default="PRIMARY,BACKUP", help="Comma-separated tiers to subtitle")
    parser.add_argument("--clip-index", type=int, default=-1, help="Subtitle only this clip index (0-based)")
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

        if args.clip_index >= 0:
            if args.clip_index < len(to_subtitle):
                to_subtitle = [to_subtitle[args.clip_index]]
            else:
                to_subtitle = []

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
                if not os.path.exists(output_path) or os.path.getsize(output_path) == 0:
                    exported.append({
                        "rank": clip_id,
                        "status": "FAILED",
                        "error": "Subtitle burn produced empty file",
                    })
                    continue
                exported.append({
                    "rank": clip_id,
                    "path": output_path,
                    "status": "COMPLETED",
                    "wordCount": len(words),
                })
            except Exception as e:
                if os.path.exists(output_path) and os.path.getsize(output_path) == 0:
                    os.remove(output_path)
                exported.append({
                    "rank": clip_id,
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
