"""Context features: pause structure (from transcript silence), topic fit
against niche keywords, history score from past feedback records."""

import json
import os


def _calc_silence_ratio(transcript_path, start_time, end_time):
    if not transcript_path or not os.path.exists(transcript_path):
        return None
    try:
        with open(transcript_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
        segments = data.get("segments", [])
        if not segments:
            return None
        in_range = [s for s in segments
                    if s.get("end", 0) > start_time and s.get("start", 0) < end_time]
        if len(in_range) < 2:
            return None
        in_range.sort(key=lambda s: s.get("start", 0))
        silence_time = 0.0
        for i in range(1, len(in_range)):
            gap = in_range[i].get("start", 0) - in_range[i - 1].get("end", 0)
            if gap > 0:
                silence_time += gap
        clip_duration = end_time - start_time
        if clip_duration <= 0:
            return None
        return silence_time / clip_duration
    except Exception:
        return None


def score_pause_structure(text, duration, transcript_path=None, start_time=0.0, end_time=0.0):
    silence_ratio = _calc_silence_ratio(transcript_path, start_time, end_time)
    if silence_ratio is not None:
        if silence_ratio < 0.10:
            return 0.9
        if silence_ratio < 0.25:
            return 0.7
        if silence_ratio < 0.40:
            return 0.4
        return 0.1

    word_count = len(text.split())
    if word_count == 0:
        return 0.3
    words_per_sec = word_count / max(duration, 1)
    if 2.0 <= words_per_sec <= 4.0:
        return 0.9
    if 1.5 <= words_per_sec <= 5.0:
        return 0.7
    if words_per_sec < 1.5:
        return 0.4
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


def score_history(feedback_data=None, text=None):
    if not feedback_data or not text:
        return 0.5
    try:
        text_words = set(text.lower().split())
        scored = []
        for record in feedback_data:
            if record.get("actual_viral_score") is None:
                continue
            rec_words = set(record.get("text", "").lower().split())
            overlap = len(text_words & rec_words)
            if overlap >= 3:
                scored.append((overlap, record["actual_viral_score"]))
        if not scored:
            return 0.5
        scored.sort(key=lambda x: x[0], reverse=True)
        top = scored[:3]
        avg = sum(s for _, s in top) / len(top)
        return round(avg, 4)
    except Exception:
        return 0.5
