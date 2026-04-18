#!/usr/bin/env python3
"""Find candidate clip segments from a transcript."""

import argparse
import json
import sys
import os


def find_segments(transcript_segments, min_duration=10, max_duration=60):
    segments = []
    i = 0
    while i < len(transcript_segments):
        start_time = transcript_segments[i]["start"]
        end_time = transcript_segments[i]["end"]
        text_parts = [transcript_segments[i]["text"]]

        j = i + 1
        while j < len(transcript_segments):
            candidate_end = transcript_segments[j]["end"]
            candidate_duration = candidate_end - start_time

            if candidate_duration > max_duration:
                break

            gap = transcript_segments[j]["start"] - transcript_segments[j - 1]["end"]
            if gap > 2.0 and candidate_duration >= min_duration:
                break

            end_time = candidate_end
            text_parts.append(transcript_segments[j]["text"])
            j += 1

        duration = end_time - start_time
        if duration >= min_duration:
            text = " ".join(text_parts)
            reason = _segment_reason(transcript_segments[i], gap=2.0)
            segments.append({
                "index": len(segments),
                "startTime": round(start_time, 2),
                "endTime": round(end_time, 2),
                "duration": round(duration, 2),
                "text": text,
                "reason": reason,
            })

        i = j if j > i + 1 else i + 1

    return segments


def _segment_reason(first_seg, gap=2.0):
    text = first_seg.get("text", "")
    reasons = []
    if "?" in text:
        reasons.append("starts with question")
    hook_words = ["rahasia", "penting", "perhatikan", "tahukah", "ternyata", "simak"]
    if any(w in text.lower() for w in hook_words):
        reasons.append("strong opening hook")
    if gap > 1.5:
        reasons.append("topic shift")
    return " + ".join(reasons) if reasons else "continuous segment"


def main():
    parser = argparse.ArgumentParser(description="Find segments in transcript")
    parser.add_argument("--transcript", required=True, help="Path to transcript JSON")
    parser.add_argument("--output", help="Path to write segments JSON")
    parser.add_argument("--min-duration", type=int, default=10, help="Min segment seconds")
    parser.add_argument("--max-duration", type=int, default=60, help="Max segment seconds")
    args = parser.parse_args()

    try:
        with open(args.transcript, "r", encoding="utf-8") as f:
            transcript = json.load(f)

        transcript_segments = transcript.get("segments", [])
        segments = find_segments(transcript_segments, args.min_duration, args.max_duration)

        result = {
            "videoId": transcript.get("videoId", ""),
            "segmentCount": len(segments),
            "segments": segments,
        }

        if args.output:
            os.makedirs(os.path.dirname(args.output), exist_ok=True)
            with open(args.output, "w", encoding="utf-8") as f:
                json.dump(result, f, ensure_ascii=False, indent=2)

        output = {"success": True, "data": result}
        print(json.dumps(output, ensure_ascii=False))

    except Exception as e:
        error_output = {"success": False, "error": str(e)}
        print(json.dumps(error_output, ensure_ascii=False), file=sys.stdout)
        print(f"ERROR: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
