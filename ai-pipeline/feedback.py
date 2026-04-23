#!/usr/bin/env python3
"""Feedback collection and viral score calculation.

Raw TikTok counts (views / likes / comments / shares / saves) are meaningless
without knowing how long the clip has been on the platform — 10k views in 2
hours is a very different signal than 10k views in 2 months. The viral score
here is therefore a *velocity* + *engagement quality* metric, not a raw total.

Formula (all four components capped at 1.0, then weighted):
  velocity   views-per-day, log-scaled (1k/day ~ 0.5, 100k/day ~ 0.95, 1M/day = 1.0)
  engagement total-interactions-per-view (5% = 1.0)
  save_rate  saves-per-view (1% = 1.0; strong TikTok retention signal)
  comment_rate comments-per-view (0.5% = 1.0)
Weights: velocity 0.35, engagement 0.25, save 0.20, comment 0.20.
"""

import argparse
import json
import math
import sys


def calculate_viral_score(views, likes, comments, shares, saves,
                          hours_since_post=1.0, followers=0):
    if views == 0:
        return 0.0

    hours = max(hours_since_post, 0.1)  # avoid div-by-zero for freshly-posted
    views_per_day = views / (hours / 24.0)

    engagement = (likes + comments + shares + saves) / max(views, 1)
    save_rate = saves / max(views, 1)
    comment_rate = comments / max(views, 1)

    velocity_score = min(math.log10(max(views_per_day, 1)) / 6.0, 1.0)
    engagement_score = min(engagement * 20, 1.0)
    save_score = min(save_rate * 100, 1.0)
    comment_score = min(comment_rate * 200, 1.0)

    score = (
        velocity_score * 0.35
        + engagement_score * 0.25
        + save_score * 0.20
        + comment_score * 0.20
    )
    return round(min(score, 1.0), 4)


def main():
    parser = argparse.ArgumentParser(description="Feedback and viral score tools")
    parser.add_argument("--action", required=True, choices=["calc-viral-score"])
    parser.add_argument("--views", type=int, default=0)
    parser.add_argument("--likes", type=int, default=0)
    parser.add_argument("--comments", type=int, default=0)
    parser.add_argument("--shares", type=int, default=0)
    parser.add_argument("--saves", type=int, default=0)
    parser.add_argument(
        "--hours-since-post", type=float, default=1.0,
        help="Hours since the clip was posted to TikTok (must be > 0).",
    )
    # Retained for backward compat — ignored by the new formula.
    parser.add_argument("--followers", type=int, default=0)
    args = parser.parse_args()

    try:
        if args.action == "calc-viral-score":
            viral_score = calculate_viral_score(
                args.views, args.likes, args.comments,
                args.shares, args.saves,
                hours_since_post=args.hours_since_post,
            )
            result = {
                "success": True,
                "data": {
                    "viralScore": viral_score,
                    "views": args.views,
                    "likes": args.likes,
                    "comments": args.comments,
                    "shares": args.shares,
                    "saves": args.saves,
                    "hoursSincePost": args.hours_since_post,
                },
            }
            print(json.dumps(result, ensure_ascii=False))
    except Exception as e:
        print(json.dumps({"success": False, "error": str(e)}, ensure_ascii=False))
        sys.exit(1)


if __name__ == "__main__":
    main()
