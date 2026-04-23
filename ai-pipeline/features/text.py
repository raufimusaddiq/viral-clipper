"""Text-only scoring features: hook, keyword, novelty, clarity, sentiment,
and the text-driven boost / penalty calculators.

Pure Python, no I/O, no heavy deps — cheap to unit-test on frozen fixtures.

P3.5-B adds an optional Indonesian TikTok corpus: if ``data/corpus/*.txt``
files exist, ``score_keyword_trigger`` blends corpus-derived TF-IDF with the
static ``KEYWORD_TRIGGERS`` list. No corpus → unchanged behavior.
"""

import math
import os
import re
import threading as _threading

from .constants import (
    HOOK_PHRASES,
    KEYWORD_TRIGGERS,
    QUESTION_WORDS,
    EMOTION_WORDS,
    CONFLICT_WORDS,
    CONVERSATION_MARKERS,
    POSITIVE_WORDS,
    NEGATIVE_WORDS,
    BOOST_CONDITIONS,
    PENALTY_CONDITIONS,
)


# --- TF-IDF corpus --------------------------------------------------------------
# Cached on first use. {token: idf_weight} for the top-N most-frequent tokens
# seen in the corpus. None means "no corpus found, use static list".
_CORPUS_IDF = None
_CORPUS_LOCK = _threading.Lock()
_CORPUS_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "..", "data", "corpus",
)
_TOKEN_RE = re.compile(r"\b[\wÀ-ÿ]{3,}\b", re.UNICODE)


def _load_keyword_corpus():
    """Scan ``data/corpus/*.txt`` for Indonesian transcripts. Return either a
    ``{token: idf}`` dict (if ≥2 docs found) or ``None`` for the fallback.
    Each .txt file is treated as one document.
    """
    global _CORPUS_IDF
    if _CORPUS_IDF is not None:
        return _CORPUS_IDF if _CORPUS_IDF else None
    with _CORPUS_LOCK:
        if _CORPUS_IDF is not None:
            return _CORPUS_IDF if _CORPUS_IDF else None
        corpus_dir = os.path.abspath(_CORPUS_DIR)
        if not os.path.isdir(corpus_dir):
            _CORPUS_IDF = {}  # sentinel: scanned, nothing found
            return None
        docs = []
        for name in os.listdir(corpus_dir):
            if not name.lower().endswith(".txt"):
                continue
            path = os.path.join(corpus_dir, name)
            try:
                with open(path, "r", encoding="utf-8") as f:
                    tokens = set(_TOKEN_RE.findall(f.read().lower()))
                if tokens:
                    docs.append(tokens)
            except Exception:
                continue
        n_docs = len(docs)
        if n_docs < 2:
            _CORPUS_IDF = {}
            return None
        df = {}
        for doc_tokens in docs:
            for tok in doc_tokens:
                df[tok] = df.get(tok, 0) + 1
        # IDF with smoothing: high for rare, low for ubiquitous. Only keep
        # tokens that appear in ≥2 docs and <80% of docs (stopword filter).
        idf = {}
        for tok, freq in df.items():
            if freq < 2 or freq > 0.8 * n_docs:
                continue
            idf[tok] = math.log(1 + n_docs / freq)
        _CORPUS_IDF = idf if idf else {}
        return idf if idf else None


def _score_keyword_trigger_tfidf(text, idf):
    tokens = _TOKEN_RE.findall(text.lower())
    if not tokens:
        return 0.1
    tf = {}
    for t in tokens:
        tf[t] = tf.get(t, 0) + 1
    total = 0.0
    matched = 0
    for t, count in tf.items():
        w = idf.get(t)
        if w is None:
            continue
        total += count * w
        matched += 1
    if matched == 0:
        return 0.1
    # Normalize by text length so long segments aren't unfairly boosted.
    density = total / max(len(tokens), 1)
    if density > 0.8:
        return 1.0
    if density > 0.5:
        return 0.85
    if density > 0.25:
        return 0.65
    if density > 0.1:
        return 0.4
    return 0.2


def _extract_first_sentence(text):
    for delim in [".", "?", "!"]:
        idx = text.find(delim)
        if idx > 0:
            return text[:idx + 1]
    if "," in text:
        return text.split(",")[0]
    return text


def score_hook_strength(text):
    first_sentence = _extract_first_sentence(text)
    lower = first_sentence.lower()
    is_question = "?" in first_sentence
    has_hook = any(p in lower for p in HOOK_PHRASES)
    has_question_word = any(w in lower.split() for w in QUESTION_WORDS[:5])
    is_short = len(first_sentence.split()) < 10

    if is_question and has_hook:
        return 1.0
    if has_hook and is_short:
        return 0.9
    if is_question or has_hook:
        return 0.7
    if has_question_word:
        return 0.6
    if is_short:
        return 0.4
    return 0.2


def score_keyword_trigger(text):
    # Prefer corpus-derived TF-IDF when ``data/corpus/*.txt`` is populated;
    # otherwise fall back to the hand-curated KEYWORD_TRIGGERS list. Both paths
    # return values on the same [0.1, 1.0] scale so the final score is
    # dimensionally comparable before and after corpus harvest.
    idf = _load_keyword_corpus()
    if idf:
        return _score_keyword_trigger_tfidf(text, idf)
    lower = text.lower()
    count = sum(1 for kw in KEYWORD_TRIGGERS if kw in lower)
    density = count / max(len(text.split()) / 10, 1)
    if count >= 4 or density > 0.8:
        return 1.0
    if count >= 3:
        return 0.85
    if count == 2:
        return 0.65
    if count == 1:
        return 0.4
    return 0.1


def score_novelty(text):
    s = 0.15
    lower = text.lower()
    if any(c.isdigit() for c in text):
        s += 0.25
    proper_nouns = sum(1 for w in text.split() if len(w) > 3 and w[0].isupper())
    if proper_nouns >= 2:
        s += 0.25
    elif proper_nouns >= 1:
        s += 0.15
    step_words = ["pertama", "kedua", "ketiga", "keempat", "terakhir"]
    if any(w in lower for w in step_words):
        s += 0.2
    surprise_words = ["ternyata", "nyatanya", "faktanya", "malah", "justru", "bohong"]
    if any(w in lower for w in surprise_words):
        s += 0.2
    return min(s, 1.0)


def score_clarity(duration, text):
    if duration < 15:
        base = 0.9
    elif duration < 30:
        base = 0.85
    elif duration < 45:
        base = 0.7
    elif duration < 60:
        base = 0.55
    else:
        base = 0.4
    context_words = ["jadi", "maksudnya", "singkatnya", "intinya", "artinya", "maksud gue"]
    if any(w in text.lower() for w in context_words):
        base = min(base + 0.15, 1.0)
    return base


def score_text_sentiment(text):
    lower = text.lower()
    pos = sum(1 for w in POSITIVE_WORDS if w in lower)
    neg = sum(1 for w in NEGATIVE_WORDS if w in lower)
    total = pos + neg
    if total == 0:
        return 0.5
    pos_ratio = pos / total
    confidence = min(total / 5.0, 1.0)
    sentiment = 0.5 + (pos_ratio - 0.5) * confidence
    return round(max(0.0, min(1.0, sentiment)), 4)


def calc_boosts(text):
    total = 0.0
    lower = text.lower()
    first_sentence = _extract_first_sentence(text)

    if "?" in first_sentence and any(w in first_sentence.lower() for w in QUESTION_WORDS):
        total += BOOST_CONDITIONS["sharp_question"]
    if any(w in lower for w in CONFLICT_WORDS):
        total += BOOST_CONDITIONS["opinion_conflict"]
    list_words = ["pertama", "kedua", "ketiga", "1", "2", "3"]
    if any(w in lower for w in list_words):
        total += BOOST_CONDITIONS["number_list"]
    if any(w in lower for w in EMOTION_WORDS):
        total += BOOST_CONDITIONS["emotional_moment"]
    marker_count = sum(1 for m in CONVERSATION_MARKERS if m in lower.split())
    if marker_count >= 3:
        total += BOOST_CONDITIONS["conversational_tone"]
    return round(total, 2)


def calc_penalties(text, duration, scores=None):
    total = 0.0
    first_5_words = " ".join(text.split()[:5])
    hook_in_start = any(p in first_5_words.lower() for p in HOOK_PHRASES) or "?" in first_5_words
    if not hook_in_start:
        total += PENALTY_CONDITIONS["slow_opening"]
    word_count = len(text.split())
    if word_count < 8 and not any(p in text.lower() for p in HOOK_PHRASES + KEYWORD_TRIGGERS):
        total += PENALTY_CONDITIONS["too_generic"]
    if scores:
        if scores.get("pauseStructure", 1.0) < 0.3:
            total += PENALTY_CONDITIONS["too_much_silence"]
        if scores.get("facePresence", 1.0) <= 0.0:
            total += PENALTY_CONDITIONS["no_face"]
    return round(total, 2)
