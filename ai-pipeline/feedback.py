#!/usr/bin/env python3
"""Feedback collection and viral score calculation."""

import argparse
import json
import math
import sys


def calculate_viral_score(views, likes, comments, shares, saves, followers=0):
    if views == 0:
        return 0.0
    engagement = (likes + comments + shares + saves) / max(views, 1)
    viral_ratio = views / max(followers, 100)
    view_score = min(math.log10(max(views, 1)) / 7, 1.0)
    score = (
        view_score * 0.4
        + min(engagement * 20, 1.0) * 0.35
        + min(math.log10(max(viral_ratio, 1)) / 4, 1.0) * 0.25
    )
    return round(min(score, 1.0), 4)


def main():
    parser = argparse.ArgumentParser(description="Feedback and viral score tools")
    parser.add_argument("--action", required=True, choices=["calc-viral-score"],
                        help="Action to perform")
    parser.add_argument("--views", type=int, default=0)
    parser.add_argument("--likes", type=int, default=0)
    parser.add_argument("--comments", type=int, default=0)
    parser.add_argument("--shares", type=int, default=0)
    parser.add_argument("--saves", type=int, default=0)
    parser.add_argument("--followers", type=int, default=0)
    args = parser.parse_args()

    try:
        if args.action == "calc-viral-score":
            viral_score = calculate_viral_score(
                args.views, args.likes, args.comments,
                args.shares, args.saves, args.followers,
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
                },
            }
            print(json.dumps(result, ensure_ascii=False))
    except Exception as e:
        print(json.dumps({"success": False, "error": str(e)}, ensure_ascii=False))
        sys.exit(1)


if __name__ == "__main__":
    main()
