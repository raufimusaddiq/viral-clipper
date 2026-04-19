#!/usr/bin/env python3
"""Learn scoring weights from feedback data using Pearson correlation + EMA."""

import argparse
import json
import math
import os
import sys


SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
WEIGHTS_PATH = os.path.join(SCRIPT_DIR, "weights.json")

FEATURE_KEYS = [
    "hookStrength", "keywordTrigger", "novelty", "clarity",
    "emotionalEnergy", "textSentiment", "pauseStructure", "facePresence",
    "sceneChange", "topicFit", "historyScore",
]


def pearson_correlation(x_vals, y_vals):
    n = len(x_vals)
    if n < 2:
        return 0.0
    mean_x = sum(x_vals) / n
    mean_y = sum(y_vals) / n
    cov = sum((x - mean_x) * (y - mean_y) for x, y in zip(x_vals, y_vals))
    var_x = sum((x - mean_x) ** 2 for x in x_vals)
    var_y = sum((y - mean_y) ** 2 for y in y_vals)
    denom = math.sqrt(var_x * var_y)
    if denom == 0:
        return 0.0
    return cov / denom


def load_current_weights():
    try:
        with open(WEIGHTS_PATH, "r", encoding="utf-8") as f:
            data = json.load(f)
        return data
    except Exception:
        return {
            "version": 0,
            "trained_on": 0,
            "last_updated": "",
            "weights": {k: round(1.0 / len(FEATURE_KEYS), 4) for k in FEATURE_KEYS},
        }


def train_weights(feedback_records, alpha=0.3, min_samples=5):
    current = load_current_weights()
    current_weights = current.get("weights", {})

    valid = [r for r in feedback_records if r.get("actual_viral_score") is not None]
    if len(valid) < min_samples:
        return {
            "success": True,
            "data": {
                "status": "insufficient_data",
                "message": f"Need at least {min_samples} records with actual scores, got {len(valid)}",
                "trained_on": current.get("trained_on", 0),
            },
        }

    feature_correlations = {}
    for feature in FEATURE_KEYS:
        pairs = []
        for r in valid:
            features = r.get("features", {})
            if isinstance(features, str):
                try:
                    features = json.loads(features)
                except Exception:
                    continue
            fv = features.get(feature)
            avs = r.get("actual_viral_score")
            if fv is not None and avs is not None:
                pairs.append((fv, avs))
        if len(pairs) >= min_samples:
            x_vals = [p[0] for p in pairs]
            y_vals = [p[1] for p in pairs]
            corr = pearson_correlation(x_vals, y_vals)
            feature_correlations[feature] = max(corr, 0.05)
        else:
            feature_correlations[feature] = current_weights.get(feature, 0.1)

    total = sum(feature_correlations.values())
    if total == 0:
        new_weights = dict(current_weights)
    else:
        raw_new = {k: v / total for k, v in feature_correlations.items()}
        blended = {}
        for k in FEATURE_KEYS:
            old = current_weights.get(k, 0.1)
            new = raw_new.get(k, old)
            blended[k] = round(old * (1 - alpha) + new * alpha, 4)
        total_b = sum(blended.values())
        if total_b > 0:
            new_weights = {k: round(v / total_b, 4) for k, v in blended.items()}
        else:
            new_weights = dict(current_weights)

    from datetime import datetime, timezone
    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    updated = {
        "version": current.get("version", 0) + 1,
        "trained_on": len(valid),
        "last_updated": now,
        "weights": new_weights,
    }

    with open(WEIGHTS_PATH, "w", encoding="utf-8") as f:
        json.dump(updated, f, ensure_ascii=False, indent=2)

    changes = {}
    for k in FEATURE_KEYS:
        old = round(current_weights.get(k, 0), 4)
        new = round(new_weights.get(k, 0), 4)
        if abs(old - new) > 0.0001:
            changes[k] = {"from": old, "to": new}

    return {
        "success": True,
        "data": {
            "status": "trained",
            "version": updated["version"],
            "trained_on": len(valid),
            "weight_changes": changes,
            "new_weights": new_weights,
        },
    }


def main():
    parser = argparse.ArgumentParser(description="Learn scoring weights from feedback")
    parser.add_argument("--action", required=True, choices=["train", "status"],
                        help="Action to perform")
    parser.add_argument("--feedback", help="Path to feedback JSON file")
    parser.add_argument("--min-samples", type=int, default=5)
    args = parser.parse_args()

    try:
        if args.action == "status":
            current = load_current_weights()
            result = {
                "success": True,
                "data": {
                    "version": current.get("version", 0),
                    "trained_on": current.get("trained_on", 0),
                    "last_updated": current.get("last_updated", ""),
                    "weights": current.get("weights", {}),
                },
            }
            print(json.dumps(result, ensure_ascii=False))

        elif args.action == "train":
            if not args.feedback:
                raise ValueError("--feedback required for train action")
            with open(args.feedback, "r", encoding="utf-8") as f:
                feedback_records = json.load(f)
            result = train_weights(feedback_records, min_samples=args.min_samples)
            print(json.dumps(result, ensure_ascii=False))

    except Exception as e:
        print(json.dumps({"success": False, "error": str(e)}, ensure_ascii=False))
        print(f"ERROR: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
