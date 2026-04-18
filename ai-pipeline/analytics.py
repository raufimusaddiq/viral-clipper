#!/usr/bin/env python3
"""Generate analytics for scored segments: distribution, recommendations, summary."""

import argparse
import json
import os
import sys


def calc_tier_distribution(scored_segments):
    tiers = {"PRIMARY": 0, "BACKUP": 0, "SKIP": 0}
    for seg in scored_segments:
        tier = seg.get("tier", "SKIP")
        tiers[tier] = tiers.get(tier, 0) + 1
    return tiers


def calc_score_stats(scored_segments):
    scores = [s.get("finalScore", 0) for s in scored_segments]
    if not scores:
        return {"min": 0, "max": 0, "avg": 0, "count": 0}
    return {
        "min": round(min(scores), 4),
        "max": round(max(scores), 4),
        "avg": round(sum(scores) / len(scores), 4),
        "count": len(scores),
    }


def calc_duration_stats(scored_segments):
    durations = [s.get("duration", 0) for s in scored_segments]
    if not durations:
        return {"min": 0, "max": 0, "avg": 0}
    return {
        "min": round(min(durations), 1),
        "max": round(max(durations), 1),
        "avg": round(sum(durations) / len(durations), 1),
    }


def calc_top_features(scored_segments):
    feature_totals = {}
    feature_counts = {}
    for seg in scored_segments:
        scores = seg.get("scores", {})
        for key, val in scores.items():
            if key in ("boostTotal", "penaltyTotal"):
                continue
            feature_totals[key] = feature_totals.get(key, 0) + val
            feature_counts[key] = feature_counts.get(key, 0) + 1
    if not feature_totals:
        return []
    averages = {k: round(v / feature_counts[k], 4) for k, v in feature_totals.items()}
    sorted_features = sorted(averages.items(), key=lambda x: x[1], reverse=True)
    return [{"feature": k, "average": v} for k, v in sorted_features]


def generate_recommendations(tier_dist, score_stats, duration_stats):
    recs = []
    if tier_dist.get("PRIMARY", 0) == 0:
        recs.append("No PRIMARY clips found. Consider longer source video or adjusting scoring weights.")
    if tier_dist.get("PRIMARY", 0) > 5:
        recs.append("Many PRIMARY clips found. Consider raising the PRIMARY threshold from 0.80.")
    if score_stats.get("avg", 0) < 0.5:
        recs.append("Average score is low. Content may not match viral keywords well.")
    if duration_stats.get("avg", 0) > 50:
        recs.append("Clips are long on average. Consider shorter segments for better TikTok performance.")
    if duration_stats.get("avg", 0) < 15:
        recs.append("Clips are very short. May need more context for viewers.")
    if not recs:
        recs.append("Scoring looks healthy. Good distribution of clip tiers.")
    return recs


def main():
    parser = argparse.ArgumentParser(description="Generate analytics for scored segments")
    parser.add_argument("--segments", required=True, help="Path to scored segments JSON")
    parser.add_argument("--output", help="Path to write analytics JSON")
    args = parser.parse_args()

    try:
        with open(args.segments, "r", encoding="utf-8") as f:
            data = json.load(f)

        scored_segments = data.get("scoredSegments", [])
        tier_dist = calc_tier_distribution(scored_segments)
        score_stats = calc_score_stats(scored_segments)
        duration_stats = calc_duration_stats(scored_segments)
        top_features = calc_top_features(scored_segments)
        recommendations = generate_recommendations(tier_dist, score_stats, duration_stats)

        result = {
            "videoId": data.get("videoId", ""),
            "totalSegments": len(scored_segments),
            "tierDistribution": tier_dist,
            "scoreStats": score_stats,
            "durationStats": duration_stats,
            "topFeatures": top_features,
            "recommendations": recommendations,
        }

        if args.output:
            os.makedirs(os.path.dirname(args.output), exist_ok=True) if os.path.dirname(args.output) else None
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
