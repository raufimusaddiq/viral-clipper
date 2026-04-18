#!/usr/bin/env python3
"""Score segments for viral potential using weighted formula."""

import argparse
import json
import sys
import os


WEIGHTS = {
    "hookStrength": 0.20,
    "keywordTrigger": 0.10,
    "novelty": 0.10,
    "clarity": 0.10,
    "emotionalEnergy": 0.10,
    "pauseStructure": 0.07,
    "facePresence": 0.10,
    "sceneChange": 0.08,
    "topicFit": 0.08,
    "historyScore": 0.07,
}

HOOK_PHRASES = [
    "rahasia", "penting", "perhatikan", "simak", "tahukah kamu",
    "tidak banyak orang tahu", "jangan", "wajib", "harus",
]

KEYWORD_TRIGGERS = [
    "rahasia", "penting", "tidak banyak orang tahu", "kesalahan", "ternyata",
    "wajib", "harus", "jangan", "bahaya", "untung", "sayangnya", "fakta",
    "curhat", "jebakan", "trik", "hack", "tip", "solusi",
]

BOOST_CONDITIONS = {
    "sharp_question": 0.05,
    "opinion_conflict": 0.05,
    "number_list": 0.03,
    "emotional_moment": 0.05,
}

PENALTY_CONDITIONS = {
    "slow_opening": 0.08,
    "too_much_silence": 0.07,
    "too_generic": 0.05,
    "no_face": 0.04,
}


def score_hook_strength(text):
    first_sentence = text.split(".")[0] if "." in text else text.split(",")[0]
    is_question = "?" in first_sentence
    has_hook = any(p in first_sentence.lower() for p in HOOK_PHRASES)
    is_short = len(first_sentence.split()) < 10

    if is_question and has_hook:
        return 1.0
    if is_question or has_hook:
        return 0.7
    if is_short:
        return 0.4
    return 0.3


def score_keyword_trigger(text):
    lower = text.lower()
    count = sum(1 for kw in KEYWORD_TRIGGERS if kw in lower)
    if count >= 3:
        return 1.0
    if count == 2:
        return 0.7
    if count == 1:
        return 0.4
    return 0.0


def score_novelty(text):
    s = 0.2
    if any(c.isdigit() for c in text):
        s += 0.3
    if any(w[0].isupper() for w in text.split() if len(w) > 3):
        s += 0.3
    step_words = ["pertama", "kedua", "ketiga", "keempat", "pertama-tama"]
    if any(w in text.lower() for w in step_words):
        s += 0.2
    return min(s, 1.0)


def score_clarity(duration, text):
    if duration < 30:
        base = 0.9
    elif duration < 45:
        base = 0.6
    else:
        base = 0.4
    context_words = ["jadi", "maksudnya", "singkatnya", "intinya"]
    if any(w in text.lower() for w in context_words):
        base = min(base + 0.2, 1.0)
    return base


def score_emotional_energy():
    return 0.5


def score_pause_structure():
    return 0.6


def score_face_presence():
    return 0.5


def score_scene_change():
    return 0.5


def score_topic_fit(text, niche_keywords):
    if not niche_keywords:
        return 0.5
    lower = text.lower()
    matches = sum(1 for kw in niche_keywords if kw in lower)
    total = len(niche_keywords)
    ratio = matches / total if total > 0 else 0
    if ratio > 0.3:
        return 1.0
    if ratio > 0.1:
        return 0.7
    return 0.3


def score_history():
    return 0.5


def calc_boosts(text):
    total = 0.0
    lower = text.lower()
    first_sentence = text.split(".")[0] if "." in text else text

    if "?" in first_sentence and any(w in first_sentence.lower() for w in ["kok", "kenapa", "bagaimana", "apa"]):
        total += BOOST_CONDITIONS["sharp_question"]
    conflict_words = ["tapi", "namun", "sebenarnya", "beda sama"]
    if any(w in lower for w in conflict_words):
        total += BOOST_CONDITIONS["opinion_conflict"]
    list_words = ["pertama", "kedua", "ketiga", "1", "2", "3"]
    if any(w in lower for w in list_words):
        total += BOOST_CONDITIONS["number_list"]
    emotion_words = ["sedih", "marah", "kaget", "wow", "amazing"]
    if any(w in lower for w in emotion_words):
        total += BOOST_CONDITIONS["emotional_moment"]
    return round(total, 2)


def calc_penalties(text, duration, scores=None):
    total = 0.0
    first_5_words = " ".join(text.split()[:5])
    hook_in_start = any(p in first_5_words.lower() for p in HOOK_PHRASES) or "?" in first_5_words
    if not hook_in_start:
        total += PENALTY_CONDITIONS["slow_opening"]
    generic_phrases = ["jadi", "terus", "lalu", "kemudian"]
    word_count = len(text.split())
    if word_count < 8 and not any(p in text.lower() for p in HOOK_PHRASES + KEYWORD_TRIGGERS):
        total += PENALTY_CONDITIONS["too_generic"]
    if scores:
        if scores.get("pauseStructure", 1.0) < 0.3:
            total += PENALTY_CONDITIONS["too_much_silence"]
        if scores.get("facePresence", 1.0) <= 0.0:
            total += PENALTY_CONDITIONS["no_face"]
    return round(total, 2)


def determine_tier(score):
    if score >= 0.80:
        return "PRIMARY"
    if score >= 0.65:
        return "BACKUP"
    return "SKIP"


def score_segment(segment, niche_keywords=None):
    text = segment.get("text", "")
    duration = segment.get("duration", 30)

    scores = {
        "hookStrength": score_hook_strength(text),
        "keywordTrigger": score_keyword_trigger(text),
        "novelty": score_novelty(text),
        "clarity": score_clarity(duration, text),
        "emotionalEnergy": score_emotional_energy(),
        "pauseStructure": score_pause_structure(),
        "facePresence": score_face_presence(),
        "sceneChange": score_scene_change(),
        "topicFit": score_topic_fit(text, niche_keywords),
        "historyScore": score_history(),
    }

    base_score = sum(WEIGHTS[k] * v for k, v in scores.items())
    boosts = calc_boosts(text)
    penalties = calc_penalties(text, duration, scores)
    final_score = round(base_score + boosts - penalties, 4)

    return {
        **segment,
        "scores": {**scores, "boostTotal": boosts, "penaltyTotal": penalties},
        "finalScore": final_score,
        "tier": determine_tier(final_score),
    }


def main():
    parser = argparse.ArgumentParser(description="Score segments for viral potential")
    parser.add_argument("--segments", required=True, help="Path to segments JSON")
    parser.add_argument("--video", required=True, help="Path to raw video file")
    parser.add_argument("--niche-keywords", default="", help="Comma-separated niche keywords")
    args = parser.parse_args()

    try:
        with open(args.segments, "r", encoding="utf-8") as f:
            data = json.load(f)

        niche_keywords = [k.strip() for k in args.niche_keywords.split(",") if k.strip()] if args.niche_keywords else None
        segments = data.get("segments", [])
        scored = [score_segment(s, niche_keywords) for s in segments]
        scored.sort(key=lambda x: x["finalScore"], reverse=True)

        for i, s in enumerate(scored):
            s["rank"] = i + 1

        result = {
            "videoId": data.get("videoId", ""),
            "scoredSegments": scored,
        }

        with open(args.segments, "w", encoding="utf-8") as f:
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
