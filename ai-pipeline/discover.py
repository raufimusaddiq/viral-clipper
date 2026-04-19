#!/usr/bin/env python3
"""YouTube video discovery via yt-dlp — search, trending, channel monitoring."""

import argparse
import json
import subprocess
import sys
import os
import re
from datetime import datetime, timezone


KEYWORDS = [
    "rahasia", "penting", "tidak banyak orang tahu", "kesalahan", "ternyata",
    "wajib", "harus", "jangan", "bahaya", "untung", "sayangnya", "fakta",
    "curhat", "jebakan", "trik", "hack", "tip", "solusi",
    "bohong", "benar", "buktinya", "nyata", "gue", "lu", "lo",
    "banget", "parah", "gila", "serius", "beneran",
    "unik", "aneh", "langka", "jarang", "mustahil",
    "mengubah", "menginspirasi", "membuktikan", "membongkar",
    "viral", "heboh", "kontroversi", "skandal", "terbaru", "update",
    "kocak", "lucu", "ngakak", "bikin kaget", "bikin nangis",
    "motivasi", "inspirasi", "kehidupan", "sukses", "gagal",
]

SEARCH_QUERIES = {
    "viral_id": [
        "viral indonesia terbaru",
        "heboh indonesia",
        "rahasia penting indonesia",
        "fakta mengejutkan indonesia",
        "tips trik indonesia viral",
        "kisah inspiratif indonesia",
        "kontroversi indonesia",
        "curhat viral indonesia",
    ],
    "trending_id": [
        "viral clip indonesia lucu kocak",
        "streamer indonesia viral moments",
        "podcast indonesia bahas viral",
        "gamer indonesia epic moment",
        "influencer indonesia drama viral",
        "content creator indonesia terbaru viral",
    ],
}


def run_ytdlp(args, ytdlp_path="yt-dlp", timeout=120):
    cmd = [ytdlp_path] + args + ["--no-warnings", "--no-check-certificates"]
    try:
        result = subprocess.run(
            cmd, capture_output=True, text=True, timeout=timeout,
        )
        return result.stdout, result.stderr, result.returncode
    except subprocess.TimeoutExpired:
        return "", "Timeout", 1
    except FileNotFoundError:
        return "", f"yt-dlp not found at {ytdlp_path}", 1


def parse_duration(duration_str):
    if not duration_str:
        return 0
    if isinstance(duration_str, (int, float)):
        return int(duration_str)
    parts = str(duration_str).split(":")
    try:
        if len(parts) == 3:
            return int(parts[0]) * 3600 + int(parts[1]) * 60 + int(parts[2])
        elif len(parts) == 2:
            return int(parts[0]) * 60 + int(parts[1])
        return int(parts[0])
    except (ValueError, IndexError):
        return 0


def extract_video_id(url):
    patterns = [
        r"(?:v=|/v/|youtu\.be/)([a-zA-Z0-9_-]{11})",
        r"/shorts/([a-zA-Z0-9_-]{11})",
    ]
    for pattern in patterns:
        match = re.search(pattern, url)
        if match:
            return match.group(1)
    return None


def quick_relevance_score(video_meta, keywords=None):
    if keywords is None:
        keywords = KEYWORDS

    score = 0.0
    title = (video_meta.get("title") or "").lower()
    desc = (video_meta.get("description") or "").lower()
    duration = video_meta.get("duration", 0)

    title_hits = sum(1 for kw in keywords if kw in title)
    max_title = max(len(keywords) * 0.15, 1)
    score += min(title_hits / max_title, 1.0) * 0.40

    if 180 <= duration <= 1200:
        score += 0.25
    elif 120 <= duration <= 1800:
        score += 0.15

    view_count = video_meta.get("viewCount") or video_meta.get("view_count") or 0
    age_hours = video_meta.get("age_hours", 999)
    if isinstance(age_hours, (int, float)) and age_hours < 720 and view_count > 0:
        vph = view_count / max(age_hours, 1)
        if vph > 1000:
            score += 0.20
        elif vph > 100:
            score += 0.10

    desc_hits = sum(1 for kw in keywords if kw in desc)
    max_desc = max(len(keywords) * 0.1, 1)
    score += min(desc_hits / max_desc, 1.0) * 0.15

    return round(min(score, 1.0), 4)


def normalize_video(entry):
    url = entry.get("url") or entry.get("webpage_url") or ""
    if not url and entry.get("id"):
        url = f"https://www.youtube.com/watch?v={entry['id']}"

    duration = entry.get("duration")
    if duration is None:
        duration = parse_duration(entry.get("duration_string", ""))

    view_count = entry.get("view_count") or entry.get("viewCount")
    if isinstance(view_count, str):
        try:
            view_count = int(view_count.replace(",", ""))
        except ValueError:
            view_count = None

    upload_date = entry.get("upload_date") or ""
    age_hours = 999
    if upload_date and len(upload_date) >= 8:
        try:
            upload_dt = datetime.strptime(upload_date[:8], "%Y%m%d").replace(
                tzinfo=timezone.utc
            )
            age_hours = (datetime.now(timezone.utc) - upload_dt).total_seconds() / 3600
        except ValueError:
            pass

    channel = entry.get("channel") or entry.get("uploader") or entry.get("channel_id") or ""

    return {
        "videoId": extract_video_id(url) or entry.get("id", ""),
        "title": entry.get("title", ""),
        "url": url,
        "duration": int(duration) if duration else 0,
        "channel": channel,
        "viewCount": view_count,
        "uploadDate": upload_date,
        "age_hours": round(age_hours, 1),
        "description": (entry.get("description") or "")[:500],
    }


def discover_search(query, max_results=20, ytdlp_path="yt-dlp", min_duration=0, max_duration=0, dateafter=None):
    search_term = f"ytsearch{max_results}:{query}"
    args = [search_term, "--flat-playlist", "--dump-json", "--skip-download"]
    if dateafter:
        args += ["--dateafter", dateafter]

    stdout, stderr, rc = run_ytdlp(args, ytdlp_path=ytdlp_path, timeout=180)
    if rc != 0:
        raise RuntimeError(f"yt-dlp search failed: {stderr}")

    videos = []
    for line in stdout.strip().split("\n"):
        line = line.strip()
        if not line:
            continue
        try:
            entry = json.loads(line)
            vid = normalize_video(entry)
            if min_duration and vid["duration"] < min_duration:
                continue
            if max_duration and vid["duration"] > max_duration:
                continue
            vid["relevanceScore"] = quick_relevance_score(vid)
            videos.append(vid)
        except json.JSONDecodeError:
            continue

    videos.sort(key=lambda v: v["relevanceScore"], reverse=True)
    return videos


def discover_trending(max_results=20, ytdlp_path="yt-dlp", region="ID"):
    queries = SEARCH_QUERIES.get("trending_id", [
        "streamer indonesia viral",
        "podcast indonesia bahas viral",
        "gamer indonesia epic moment",
        "influencer indonesia drama viral",
    ])
    per_query = max(max_results // len(queries), 5)
    seen = set()
    all_videos = []

    for q in queries:
        try:
            batch = discover_search(q, per_query, ytdlp_path, dateafter="today-30days")
            for v in batch:
                if v["videoId"] not in seen:
                    seen.add(v["videoId"])
                    all_videos.append(v)
        except RuntimeError:
            continue

    all_videos.sort(key=lambda v: v["relevanceScore"], reverse=True)
    return all_videos[:max_results]


def discover_channel(channel_url, max_results=20, ytdlp_path="yt-dlp", min_duration=0, max_duration=0):
    videos_url = channel_url.rstrip("/") + "/videos"
    stdout, stderr, rc = run_ytdlp(
        [videos_url, "--flat-playlist", "--dump-json", "--skip-download", "--playlist-end", str(max_results)],
        ytdlp_path=ytdlp_path,
        timeout=180,
    )
    if rc != 0:
        stdout2, stderr2, rc2 = run_ytdlp(
            [channel_url, "--flat-playlist", "--dump-json", "--skip-download", "--playlist-end", str(max_results)],
            ytdlp_path=ytdlp_path,
            timeout=180,
        )
        if rc2 != 0:
            raise RuntimeError(f"yt-dlp channel failed: {stderr2}")
        stdout = stdout2

    videos = []
    for line in stdout.strip().split("\n"):
        line = line.strip()
        if not line:
            continue
        try:
            entry = json.loads(line)
            vid = normalize_video(entry)
            if min_duration and vid["duration"] < min_duration:
                continue
            if max_duration and vid["duration"] > max_duration:
                continue
            vid["relevanceScore"] = quick_relevance_score(vid)
            videos.append(vid)
        except json.JSONDecodeError:
            continue

    videos.sort(key=lambda v: v["relevanceScore"], reverse=True)
    return videos


def main():
    parser = argparse.ArgumentParser(description="YouTube video discovery")
    parser.add_argument("--mode", required=True, choices=["search", "trending", "channel"],
                        help="Discovery mode")
    parser.add_argument("--query", default="", help="Search query (for search mode)")
    parser.add_argument("--channel-url", default="", help="Channel URL (for channel mode)")
    parser.add_argument("--max-results", type=int, default=20, help="Max results to return")
    parser.add_argument("--min-duration", type=int, default=0, help="Min duration in seconds")
    parser.add_argument("--max-duration", type=int, default=0, help="Max duration in seconds (0=unlimited)")
    parser.add_argument("--region", default="ID", help="Region for trending (default: ID)")
    parser.add_argument("--ytdlp-path", default="yt-dlp", help="Path to yt-dlp binary")
    args = parser.parse_args()

    try:
        if args.mode == "search":
            if not args.query:
                raise ValueError("--query is required for search mode")
            videos = discover_search(
                args.query, args.max_results, args.ytdlp_path,
                args.min_duration, args.max_duration,
            )
        elif args.mode == "trending":
            videos = discover_trending(args.max_results, args.ytdlp_path, args.region)
        elif args.mode == "channel":
            if not args.channel_url:
                raise ValueError("--channel-url is required for channel mode")
            videos = discover_channel(
                args.channel_url, args.max_results, args.ytdlp_path,
                args.min_duration, args.max_duration,
            )
        else:
            raise ValueError(f"Unknown mode: {args.mode}")

        result = {
            "mode": args.mode,
            "query": args.query,
            "count": len(videos),
            "videos": videos,
        }

        output = {"success": True, "data": result}
        print(json.dumps(output, ensure_ascii=False))
        sys.exit(0)

    except Exception as e:
        error_output = {"success": False, "error": str(e)}
        print(json.dumps(error_output, ensure_ascii=False), file=sys.stdout)
        print(f"ERROR: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
