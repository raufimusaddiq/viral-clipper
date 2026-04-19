#!/usr/bin/env python3
"""Score segments for viral potential using weighted multimodal formula."""

import argparse
import json
import math
import os
import re
import sys


SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
WEIGHTS_PATH = os.path.join(SCRIPT_DIR, "weights.json")

DEFAULT_WEIGHTS = {
    "hookStrength": 0.18,
    "keywordTrigger": 0.09,
    "novelty": 0.09,
    "clarity": 0.10,
    "emotionalEnergy": 0.09,
    "textSentiment": 0.05,
    "pauseStructure": 0.07,
    "facePresence": 0.10,
    "sceneChange": 0.08,
    "topicFit": 0.08,
    "historyScore": 0.07,
}


def load_weights():
    try:
        with open(WEIGHTS_PATH, "r", encoding="utf-8") as f:
            data = json.load(f)
        w = data.get("weights", {})
        missing = set(DEFAULT_WEIGHTS.keys()) - set(w.keys())
        for k in missing:
            w[k] = DEFAULT_WEIGHTS[k]
        return w
    except Exception:
        return dict(DEFAULT_WEIGHTS)


WEIGHTS = load_weights()

HOOK_PHRASES = [
    "rahasia", "penting", "perhatikan", "simak", "tahukah kamu",
    "tidak banyak orang tahu", "jangan", "wajib", "harus",
    "kamu tahu tidak", "coba bayangkan", "faktanya", "sesuatu yang",
    "gila", "luar biasa", "aneh", "mengejutkan",
]

KEYWORD_TRIGGERS = [
    "rahasia", "penting", "tidak banyak orang tahu", "kesalahan", "ternyata",
    "wajib", "harus", "jangan", "bahaya", "untung", "sayangnya", "fakta",
    "curhat", "jebakan", "trik", "hack", "tip", "solusi",
    "bohong", "benar", "buktinya", "nyata", "gue", "lu", "lo",
    "banget", "parah", "gila", "serius", "beneran",
    "unik", "aneh", "langka", "jarang", "mustahil",
    "mengubah", "menginspirasi", "membuktikan", "membongkar",
]

EMOTION_WORDS = [
    "sedih", "marah", "kaget", "wow", "amazing", "senang", "kecewa",
    "bangga", "takut", "haru", "benci", "cinta", "panic",
    "shock", "greget", "geram", "syukur", "bersyukur", "penasaran",
]

POSITIVE_WORDS = [
    "bagus", "baik", "hebat", "indah", "cantik", "keren", "sukses",
    "berhasil", "bahagia", "senang", "luar biasa", "mantap", "jos",
    "top", "istimewa", "menakjubkan", "fantastis", "sempurna", "puas",
    "memuaskan", "bermanfaat", "berguna", "efektif", "mudah", "praktis",
    "hemat", "murah", "gratis", "bonus", "untung", "beruntung",
    "tepat", "akurat", "jelas", "nyata", "aman", "nyaman", "sehat",
    "kuat", "cepat", "solusi", "trik", "tip", "hack",
    "inspirasi", "motivasi", "berharga", "positif", "optimis",
    "menguntungkan", "pintar", "cerdas", "kreatif", "inovatif",
]

NEGATIVE_WORDS = [
    "jelek", "buruk", "gagal", "rusak", "hilang", "mati", "error",
    "bug", "lambat", "mahal", "boros", "ribet", "sulit", "susah",
    "payah", "lemah", "bodoh", "tolol", "sampah", "buang waktu",
    "penipuan", "tipu", "bohong", "dusta", "palsu", "kecewa",
    "sedih", "marah", "geram", "parah", "mengerikan", "bahaya",
    "bingung", "pusing", "sakit", "risiko", "ancaman", "masalah",
    "problem", "rugi", "merugikan", "negatif", "pesimis", "takut",
    "cemas", "khawatir", "stres", "depresi", "frustrasi",
]

CONVERSATION_MARKERS = [
    "kan", "ya", "dong", "sih", "kok", "nih", "tuh", "deh",
    "loh", "nah", "duh", "aduh", "wah", "ih", "eh",
]

CONFLICT_WORDS = [
    "tapi", "namun", "sebenarnya", "beda sama", "salah", "benar",
    "memang", "boleh dibilang", "sebaliknya", "padahal", "ternyata",
    "malah", "justru", "nyatanya",
]

QUESTION_WORDS = [
    "apa", "kenapa", "kok", "bagaimana", "kapan", "siapa", "dimana",
    "berapa", "mengapa", "gimana",
]

BOOST_CONDITIONS = {
    "sharp_question": 0.05,
    "opinion_conflict": 0.05,
    "number_list": 0.03,
    "emotional_moment": 0.05,
    "conversational_tone": 0.03,
}

PENALTY_CONDITIONS = {
    "slow_opening": 0.08,
    "too_much_silence": 0.07,
    "too_generic": 0.05,
    "no_face": 0.04,
}

HASHTAG_MAP = {
    "rahasia": ["#rahasia", "#faktamenarik"],
    "penting": ["#penting", "#wajibtau"],
    "trik": ["#trik", "#tips"],
    "hack": ["#hack", "#lifehack"],
    "tip": ["#tips", "#trik"],
    "bahaya": ["#bahaya", "#peringatan"],
    "ternyata": ["#ternyata", "#fakta"],
    "fakta": ["#fakta", "#faktamenarik"],
    "kaget": ["#kaget", "#wow"],
    "gila": ["#gila", "#wow"],
    "parah": ["#parah", "#viral"],
    "serius": ["#serius", "#nyata"],
    "aneh": ["#aneh", "#unik"],
    "langka": ["#langka", "#unik"],
    "solusi": ["#solusi", "#tips"],
    "jebakan": ["#jebakan", "#hatihati"],
    "sedih": ["#sedih", "#motivasi"],
    "marah": ["#marah", "#geram"],
    "cinta": ["#cinta", "#romantis"],
    "motivasi": ["#motivasi", "#inspirasi"],
    "bohong": ["#bohong", "#fakta"],
    "menginspirasi": ["#inspirasi", "#motivasi"],
}

GENERIC_HASHTAGS = ["#fyp", "#foryou", "#viral", "#tiktokindonesia"]

CTA_PHRASES = [
    "Simak sampai habis!",
    "Follow untuk konten lainnya!",
    "Share ke temen kamu!",
    "Save buat nanti!",
]


def _extract_first_sentence(text):
    for delim in [".", "?", "!"]:
        idx = text.find(delim)
        if idx > 0:
            return text[:idx + 1]
    if "," in text:
        return text.split(",")[0]
    return text


def _load_audio_cache(audio_path):
    if not audio_path or not os.path.exists(audio_path):
        return None
    try:
        import numpy as np
        import wave
        with wave.open(audio_path, 'rb') as wf:
            framerate = wf.getframerate()
            n_channels = wf.getnchannels()
            sampwidth = wf.getsampwidth()
            total_frames = wf.getnframes()
            wf.setpos(0)
            raw = wf.readframes(total_frames)
            dtype = {1: np.int8, 2: np.int16, 4: np.int32}.get(sampwidth, np.int16)
            samples = np.frombuffer(raw, dtype=dtype).astype(np.float64)
            if n_channels > 1:
                samples = samples[::n_channels]
            total_rms = np.sqrt(np.mean(samples ** 2))
            return {"samples": samples, "framerate": framerate, "total_rms": total_rms}
    except Exception:
        return None


def _extract_audio_rms_ratio(audio_path, start_time, end_time, audio_cache=None):
    try:
        import numpy as np
        if audio_cache is None:
            audio_cache = _load_audio_cache(audio_path)
        if audio_cache is None:
            return None
        samples = audio_cache["samples"]
        framerate = audio_cache["framerate"]
        total_rms = audio_cache["total_rms"]
        if total_rms == 0:
            return None
        start_sample = max(0, min(int(start_time * framerate), len(samples)))
        end_sample = max(0, min(int(end_time * framerate), len(samples)))
        if start_sample >= end_sample:
            return None
        seg = samples[start_sample:end_sample]
        seg_rms = np.sqrt(np.mean(seg ** 2))
        return seg_rms / total_rms
    except Exception:
        return None


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


def score_emotional_energy(text, audio_path=None, start_time=0.0, end_time=0.0, audio_cache=None):
    text_score = 0.3
    lower = text.lower()
    count = sum(1 for w in EMOTION_WORDS if w in lower)
    excl = text.count("!")
    caps_words = sum(1 for w in text.split() if w.isupper() and len(w) > 2)
    if count >= 3:
        text_score += 0.4
    elif count >= 2:
        text_score += 0.3
    elif count >= 1:
        text_score += 0.2
    if excl >= 2:
        text_score += 0.15
    elif excl >= 1:
        text_score += 0.1
    if caps_words >= 2:
        text_score += 0.1
    text_score = min(text_score, 1.0)

    rms_ratio = _extract_audio_rms_ratio(audio_path, start_time, end_time, audio_cache=audio_cache)
    if rms_ratio is not None:
        if rms_ratio > 1.8:
            audio_score = 1.0
        elif rms_ratio > 1.3:
            audio_score = 0.8
        elif rms_ratio > 0.8:
            audio_score = 0.5
        elif rms_ratio > 0.4:
            audio_score = 0.3
        else:
            audio_score = 0.1
        return round((text_score * 0.4 + audio_score * 0.6), 4)

    return round(text_score, 4)


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


def _extract_frames_ffmpeg(video_path, timestamps):
    import subprocess
    import tempfile
    frames = {}
    tmpdir = tempfile.mkdtemp(prefix="vc_frames_")
    try:
        ts_list = sorted(set(timestamps))
        if not ts_list:
            return frames
        start = max(0, ts_list[0] - 0.5)
        end = ts_list[-1] + 0.5
        out_pattern = os.path.join(tmpdir, "f_%06d.jpg")
        cmd = [
            "ffmpeg", "-y",
            "-ss", f"{start:.3f}", "-to", f"{end:.3f}",
            "-i", video_path,
            "-vf", "fps=1,scale=320:-2",
            "-q:v", "5",
            out_pattern
        ]
        result = subprocess.run(cmd, capture_output=True, timeout=120)
        if result.returncode == 0:
            import cv2
            import glob as _glob
            extracted = sorted(_glob.glob(os.path.join(tmpdir, "f_*.jpg")))
            for img_path in extracted:
                img = cv2.imread(img_path)
                if img is not None:
                    frame_num = int(os.path.basename(img_path).replace("f_", "").replace(".jpg", ""))
                    t = start + (frame_num - 1)
                    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
                    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
                    frames[t] = {"gray": gray, "hsv": hsv}
                try:
                    os.remove(img_path)
                except Exception:
                    pass
        else:
            for t in ts_list:
                out_path = os.path.join(tmpdir, f"s_{int(t * 1000)}.jpg")
                cmd2 = [
                    "ffmpeg", "-y", "-ss", f"{t:.3f}", "-i", video_path,
                    "-vframes", "1", "-q:v", "5", "-vf", "scale=320:-2",
                    out_path
                ]
                r = subprocess.run(cmd2, capture_output=True, timeout=15)
                if r.returncode == 0 and os.path.exists(out_path):
                    import cv2
                    img = cv2.imread(out_path)
                    if img is not None:
                        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
                        hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
                        frames[t] = {"gray": gray, "hsv": hsv}
                    try:
                        os.remove(out_path)
                    except Exception:
                        pass
    except Exception:
        pass
    finally:
        try:
            import shutil
            shutil.rmtree(tmpdir, ignore_errors=True)
        except Exception:
            pass
    return frames


def _batch_analyze_video(video_path, segments):
    if not video_path or not os.path.exists(video_path):
        return {i: {"faces": 0.5, "scene": 0.5} for i in range(len(segments))}
    try:
        import cv2
        import numpy as np

        face_cascade = cv2.CascadeClassifier(
            cv2.data.haarcascades + "haarcascade_frontalface_default.xml"
        )
        smile_path = os.path.join(cv2.data.haarcascades, "haarcascade_smile.xml")
        smile_cascade = None
        if os.path.exists(smile_path):
            smile_cascade = cv2.CascadeClassifier(smile_path)

        all_times = []
        unique_ts = set()
        for i, seg in enumerate(segments):
            start = seg.get("startTime", 0)
            dur = seg.get("duration", 30)
            end = seg.get("endTime", start + dur)
            mid = start + dur * 0.5
            ts = [mid, start, end]
            all_times.append({"idx": i, "face_times": [mid], "scene_times": [start, mid, end]})
            for t in ts:
                unique_ts.add(round(t, 1))

        unique_ts = sorted(unique_ts)
        if len(unique_ts) > 60:
            step = len(unique_ts) / 60
            unique_ts = [unique_ts[int(i * step)] for i in range(60)]

        frame_data = _extract_frames_ffmpeg(video_path, unique_ts)

        def _nearest(t):
            key = round(t, 1)
            if key in frame_data:
                return frame_data[key]
            best = None
            best_diff = float("inf")
            for k in frame_data:
                d = abs(k - t)
                if d < best_diff:
                    best_diff = d
                    best = frame_data[k]
            return best

        results = {}
        for info in all_times:
            idx = info["idx"]

            faces_found = 0
            smiles_found = 0
            for t in info["face_times"]:
                fd = _nearest(t)
                if fd is None:
                    continue
                gray = fd["gray"]
                try:
                    faces = face_cascade.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=3, minSize=(20, 20))
                    if len(faces) > 0:
                        faces_found += 1
                        if smile_cascade is not None:
                            for (fx, fy, fw, fh) in faces[:1]:
                                roi = gray[fy:fy + fh, fx:fx + fw]
                                smiles = smile_cascade.detectMultiScale(roi, scaleFactor=1.8, minNeighbors=15, minSize=(10, 10))
                                if len(smiles) > 0:
                                    smiles_found += 1
                except Exception:
                    pass

            if faces_found >= 1 and smiles_found >= 1:
                face_score = 1.0
            elif faces_found >= 1:
                face_score = 0.7
            else:
                face_score = 0.0

            scene_frames = [fd for t in info["scene_times"] if (fd := _nearest(t)) is not None]

            if len(scene_frames) >= 2:
                diffs = []
                grays = [f["gray"] for f in scene_frames]
                for i2 in range(1, len(grays)):
                    h1 = cv2.calcHist([grays[i2 - 1]], [0], None, [64], [0, 256])
                    h2 = cv2.calcHist([grays[i2]], [0], None, [64], [0, 256])
                    cv2.normalize(h1, h1)
                    cv2.normalize(h2, h2)
                    diffs.append(cv2.compareHist(h1, h2, cv2.HISTCMP_CORREL))
                avg_corr = sum(diffs) / len(diffs)
                change_score = 1.0 - avg_corr

                brightness_scores = []
                saturation_scores = []
                for f in scene_frames:
                    avg_v = np.mean(f["hsv"][:, :, 2]) / 255.0
                    avg_s = np.mean(f["hsv"][:, :, 1]) / 255.0
                    brightness_scores.append(avg_v)
                    saturation_scores.append(avg_s)
                avg_brightness = sum(brightness_scores) / len(brightness_scores) if brightness_scores else 0.5
                avg_saturation = sum(saturation_scores) / len(saturation_scores) if saturation_scores else 0.3

                visual_appeal = 0.0
                if avg_brightness > 0.5:
                    visual_appeal += 0.1
                if avg_brightness > 0.65:
                    visual_appeal += 0.1
                if avg_saturation > 0.35:
                    visual_appeal += 0.1
                if avg_saturation > 0.5:
                    visual_appeal += 0.1

                scene_score = 0.3
                if change_score > 0.5:
                    scene_score = 0.9
                elif change_score > 0.3:
                    scene_score = 0.7
                elif change_score > 0.15:
                    scene_score = 0.5
                scene_score = min(scene_score + visual_appeal, 1.0)
            else:
                scene_score = 0.5

            results[idx] = {"faces": face_score, "scene": round(scene_score, 4)}

        return results
    except Exception:
        return {i: {"faces": 0.5, "scene": 0.5} for i in range(len(segments))}


def score_face_presence(video_path=None, start_time=0.0, end_time=0.0):
    if not video_path or not os.path.exists(video_path):
        return 0.5
    try:
        import cv2
        cap = cv2.VideoCapture(video_path)
        if not cap.isOpened():
            return 0.5
        fps = cap.get(cv2.CAP_PROP_FPS)
        if fps <= 0:
            cap.release()
            return 0.5
        duration = end_time - start_time
        sample_times = [start_time + duration * f for f in [0.0, 0.5, 1.0]]
        face_cascade = cv2.CascadeClassifier(
            cv2.data.haarcascades + "haarcascade_frontalface_default.xml"
        )
        smile_cascade = None
        smile_path = os.path.join(cv2.data.haarcascades, "haarcascade_smile.xml")
        if os.path.exists(smile_path):
            smile_cascade = cv2.CascadeClassifier(smile_path)
        faces_found = 0
        smiles_found = 0
        for t in sample_times:
            frame_idx = int(t * fps)
            cap.set(cv2.CAP_PROP_POS_FRAMES, frame_idx)
            ret, frame = cap.read()
            if not ret:
                continue
            gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
            faces = face_cascade.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=3, minSize=(30, 30))
            if len(faces) > 0:
                faces_found += 1
                if smile_cascade is not None:
                    for (fx, fy, fw, fh) in faces[:1]:
                        roi_gray = gray[fy:fy + fh, fx:fx + fw]
                        smiles = smile_cascade.detectMultiScale(roi_gray, scaleFactor=1.8, minNeighbors=15, minSize=(15, 15))
                        if len(smiles) > 0:
                            smiles_found += 1
        cap.release()
        if faces_found >= 3 and smiles_found >= 2:
            return 1.0
        if faces_found >= 3:
            return 0.9
        if faces_found == 2 and smiles_found >= 1:
            return 0.85
        if faces_found == 2:
            return 0.7
        if faces_found == 1 and smiles_found >= 1:
            return 0.6
        if faces_found == 1:
            return 0.3
        return 0.0
    except Exception:
        return 0.5


def score_scene_change(video_path=None, start_time=0.0, end_time=0.0):
    if not video_path or not os.path.exists(video_path):
        return 0.5
    try:
        import cv2
        import numpy as np
        cap = cv2.VideoCapture(video_path)
        if not cap.isOpened():
            return 0.5
        fps = cap.get(cv2.CAP_PROP_FPS)
        if fps <= 0:
            cap.release()
            return 0.5
        duration = end_time - start_time
        n_samples = min(max(int(duration / 5), 3), 8)
        sample_times = [start_time + duration * i / (n_samples - 1) for i in range(n_samples)]
        frames_gray = []
        frames_hsv = []
        for t in sample_times:
            cap.set(cv2.CAP_PROP_POS_FRAMES, int(t * fps))
            ret, frame = cap.read()
            if not ret:
                continue
            frames_gray.append(cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY))
            frames_hsv.append(cv2.cvtColor(frame, cv2.COLOR_BGR2HSV))
        cap.release()
        if len(frames_gray) < 2:
            return 0.5

        diffs = []
        for i in range(1, len(frames_gray)):
            h1 = cv2.calcHist([frames_gray[i - 1]], [0], None, [64], [0, 256])
            h2 = cv2.calcHist([frames_gray[i]], [0], None, [64], [0, 256])
            cv2.normalize(h1, h1)
            cv2.normalize(h2, h2)
            diffs.append(cv2.compareHist(h1, h2, cv2.HISTCMP_CORREL))
        avg_corr = sum(diffs) / len(diffs)
        change_score = 1.0 - avg_corr

        brightness_scores = []
        saturation_scores = []
        for hsv in frames_hsv:
            avg_v = np.mean(hsv[:, :, 2]) / 255.0
            avg_s = np.mean(hsv[:, :, 1]) / 255.0
            brightness_scores.append(avg_v)
            saturation_scores.append(avg_s)
        avg_brightness = sum(brightness_scores) / len(brightness_scores) if brightness_scores else 0.5
        avg_saturation = sum(saturation_scores) / len(saturation_scores) if saturation_scores else 0.3

        visual_appeal = 0.0
        if avg_brightness > 0.5:
            visual_appeal += 0.1
        if avg_brightness > 0.65:
            visual_appeal += 0.1
        if avg_saturation > 0.35:
            visual_appeal += 0.1
        if avg_saturation > 0.5:
            visual_appeal += 0.1

        result = 0.3
        if change_score > 0.5:
            result = 0.9
        elif change_score > 0.3:
            result = 0.7
        elif change_score > 0.15:
            result = 0.5
        result = min(result + visual_appeal, 1.0)
        return round(result, 4)
    except Exception:
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


def determine_tier(score):
    if score >= 0.80:
        return "PRIMARY"
    if score >= 0.65:
        return "BACKUP"
    return "SKIP"


def generate_clip_title(text, tier, scores):
    title = ""
    sentences = re.split(r'[.?!,]', text)
    for s in sentences:
        s = s.strip()
        if not s:
            continue
        sl = s.lower()
        if "?" in text[:len(s) + 1]:
            title = s.strip()
            if not title.endswith("?"):
                title += "?"
            break
        if any(p in sl for p in HOOK_PHRASES):
            title = s.strip()
            break
    if not title:
        title = sentences[0].strip() if sentences else text.strip()
    if len(title) > 80:
        title = title[:77] + "..."
    hashtags = _select_hashtags(text, tier, count=2)
    if hashtags:
        title = title + " " + " ".join(hashtags)
    if len(title) > 100:
        title = title[:97] + "..."
    return title


def generate_clip_description(text, tier, scores):
    lines = []
    hook_line = _generate_hook_line(text, tier)
    if hook_line:
        lines.append(hook_line)
    clean_text = text.strip()
    if len(clean_text) > 150:
        clean_text = clean_text[:147] + "..."
    lines.append(clean_text)
    hashtags = _select_hashtags(text, tier, count=7)
    if hashtags:
        lines.append(" ".join(hashtags))
    cta = CTA_PHRASES[hash(text) % len(CTA_PHRASES)]
    lines.append(cta)
    return "\n".join(lines)


def _select_hashtags(text, tier, count=5):
    lower = text.lower()
    matched = []
    for kw, tags in HASHTAG_MAP.items():
        if kw in lower:
            matched.extend(tags)
    matched = list(dict.fromkeys(matched))
    base = list(GENERIC_HASHTAGS)
    combined = matched + [t for t in base if t not in matched]
    if tier == "PRIMARY":
        if "#viral" not in combined:
            combined.insert(0, "#viral")
    return combined[:count]


def _generate_hook_line(text, tier):
    lower = text.lower()
    if "?" in _extract_first_sentence(text):
        return None
    if any(w in lower for w in ["rahasia", "penting", "wajib"]):
        return "Rahasia yang wajib kamu tahu!"
    if any(w in lower for w in ["ternyata", "faktanya", "nyatanya"]):
        return "Ternyata faktanya begini!"
    if any(w in lower for w in ["trik", "hack", "tip", "solusi"]):
        return "Trik yang harus kamu coba!"
    if tier == "PRIMARY":
        return "Wajib tahu ini!"
    return None


def score_segment(segment, niche_keywords=None, video_path=None,
                  audio_path=None, transcript_path=None, feedback_data=None,
                  audio_cache=None, video_data=None):
    text = segment.get("text", "")
    duration = segment.get("duration", 30)
    start_time = segment.get("startTime", 0)
    end_time = segment.get("endTime", start_time + duration)
    seg_idx = segment.get("index", 0)

    face_score = 0.5
    scene_score_val = 0.5
    if video_data and seg_idx in video_data:
        face_score = video_data[seg_idx]["faces"]
        scene_score_val = video_data[seg_idx]["scene"]

    scores = {
        "hookStrength": score_hook_strength(text),
        "keywordTrigger": score_keyword_trigger(text),
        "novelty": score_novelty(text),
        "clarity": score_clarity(duration, text),
        "emotionalEnergy": score_emotional_energy(text, audio_path, start_time, end_time, audio_cache=audio_cache),
        "textSentiment": score_text_sentiment(text),
        "pauseStructure": score_pause_structure(text, duration, transcript_path, start_time, end_time),
        "facePresence": face_score,
        "sceneChange": scene_score_val,
        "topicFit": score_topic_fit(text, niche_keywords),
        "historyScore": score_history(feedback_data, text),
    }

    base_score = sum(WEIGHTS.get(k, 0) * v for k, v in scores.items())
    boosts = calc_boosts(text)
    penalties = calc_penalties(text, duration, scores)
    final_score = round(min(base_score + boosts - penalties, 1.0), 4)
    tier = determine_tier(final_score)

    title = generate_clip_title(text, tier, scores)
    description = generate_clip_description(text, tier, scores)

    return {
        **segment,
        "scores": {**scores, "boostTotal": boosts, "penaltyTotal": penalties},
        "finalScore": final_score,
        "tier": tier,
        "title": title,
        "description": description,
    }


def main():
    parser = argparse.ArgumentParser(description="Score segments for viral potential")
    parser.add_argument("--segments", required=True, help="Path to segments JSON")
    parser.add_argument("--video", required=True, help="Path to raw video file")
    parser.add_argument("--audio", default="", help="Path to extracted audio WAV file")
    parser.add_argument("--transcript", default="", help="Path to transcript JSON")
    parser.add_argument("--niche-keywords", default="", help="Comma-separated niche keywords")
    args = parser.parse_args()

    try:
        with open(args.segments, "r", encoding="utf-8") as f:
            data = json.load(f)

        niche_keywords = [k.strip() for k in args.niche_keywords.split(",") if k.strip()] if args.niche_keywords else None
        segments = data.get("segments", [])
        audio_path = args.audio if args.audio and os.path.exists(args.audio) else None
        transcript_path = args.transcript if args.transcript and os.path.exists(args.transcript) else None

        audio_cache = _load_audio_cache(audio_path)
        video_data = _batch_analyze_video(args.video, segments)

        scored = [score_segment(s, niche_keywords, video_path=args.video,
                                audio_path=audio_path, transcript_path=transcript_path,
                                audio_cache=audio_cache, video_data=video_data)
                  for s in segments]
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
