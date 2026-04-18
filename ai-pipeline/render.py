#!/usr/bin/env python3
"""Render 9:16 vertical clips from scored segments using ffmpeg."""

import argparse
import json
import os
import subprocess
import sys


def find_ffmpeg():
    return os.environ.get("FFMPEG_PATH", "ffmpeg")


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


def main():
    parser = argparse.ArgumentParser(description="Render 9:16 vertical clips")
    parser.add_argument("--segments", required=True, help="Path to scored segments JSON")
    parser.add_argument("--video", required=True, help="Path to source video file")
    parser.add_argument("--output-dir", required=True, help="Directory for rendered clips")
    parser.add_argument("--tiers", default="PRIMARY,BACKUP", help="Comma-separated tiers to render")
    args = parser.parse_args()

    try:
        with open(args.segments, "r", encoding="utf-8") as f:
            data = json.load(f)

        tiers = [t.strip() for t in args.tiers.split(",")]
        segments = data.get("scoredSegments", [])
        to_render = [s for s in segments if s.get("tier") in tiers]

        os.makedirs(args.output_dir, exist_ok=True)
        rendered = []

        for seg in to_render:
            clip_id = seg.get("rank", seg.get("index", 0))
            output_path = os.path.join(args.output_dir, f"clip_{clip_id}.mp4")
            try:
                render_clip(
                    args.video,
                    seg["startTime"],
                    seg["endTime"],
                    output_path,
                )
                rendered.append({
                    "rank": clip_id,
                    "tier": seg.get("tier"),
                    "path": output_path,
                    "status": "COMPLETED",
                    "startTime": seg["startTime"],
                    "endTime": seg["endTime"],
                })
            except Exception as e:
                rendered.append({
                    "rank": clip_id,
                    "tier": seg.get("tier"),
                    "path": output_path,
                    "status": "FAILED",
                    "error": str(e),
                })

        result = {
            "videoId": data.get("videoId", ""),
            "renderedCount": len([r for r in rendered if r["status"] == "COMPLETED"]),
            "failedCount": len([r for r in rendered if r["status"] == "FAILED"]),
            "clips": rendered,
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
