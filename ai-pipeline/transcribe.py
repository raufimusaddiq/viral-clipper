#!/usr/bin/env python3
"""Transcribe audio using faster-whisper with CUDA support."""

import argparse
import json
import sys
import os

def main():
    parser = argparse.ArgumentParser(description="Transcribe audio file")
    parser.add_argument("--audio", required=True, help="Path to WAV audio file")
    parser.add_argument("--output", help="Path to write transcript JSON")
    parser.add_argument("--language", default="id", help="Language code")
    parser.add_argument("--model", default="large-v3-turbo", help="Whisper model size")
    parser.add_argument("--device", default="cuda", help="Device: cuda or cpu")
    args = parser.parse_args()

    try:
        from faster_whisper import WhisperModel

        compute_type = "float16" if args.device == "cuda" else "int8"
        model = WhisperModel(args.model, device=args.device, compute_type=compute_type)

        segments_gen, info = model.transcribe(
            args.audio,
            language=args.language,
            word_timestamps=True,
            vad_filter=True,
        )

        segments = []
        for i, seg in enumerate(segments_gen):
            segments.append({
                "index": i,
                "start": round(seg.start, 2),
                "end": round(seg.end, 2),
                "text": seg.text.strip(),
                "confidence": round(seg.avg_logprob, 2) if seg.avg_logprob else None,
            })

        result = {
            "videoId": os.path.splitext(os.path.basename(args.audio))[0],
            "language": info.language,
            "model": args.model,
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
