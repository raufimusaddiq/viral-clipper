import json
import os
import subprocess
import sys
import tempfile

import pytest


SCRIPTS_DIR = os.path.join(os.path.dirname(__file__), "..")
sys.path.insert(0, os.path.abspath(SCRIPTS_DIR))


from segment import find_segments, _segment_reason
from score import (
    score_hook_strength,
    score_keyword_trigger,
    score_novelty,
    score_clarity,
    score_emotional_energy,
    score_pause_structure,
    score_face_presence,
    score_scene_change,
    score_topic_fit,
    score_history,
    calc_boosts,
    calc_penalties,
    determine_tier,
    score_segment,
    WEIGHTS,
    HOOK_PHRASES,
    KEYWORD_TRIGGERS,
    PENALTY_CONDITIONS,
)


def run_script(script_name, args):
    cmd = [sys.executable, os.path.join(SCRIPTS_DIR, script_name)] + args
    return subprocess.run(cmd, capture_output=True, text=True, timeout=120)


# ── Segment unit tests ──


class TestFindSegments:
    def test_basic_segmentation(self):
        segments = [
            {"index": 0, "start": 0.0, "end": 5.0, "text": "Halo semuanya", "confidence": 0.9},
            {"index": 1, "start": 5.5, "end": 15.0, "text": "hari ini bahas rahasia penting", "confidence": 0.9},
            {"index": 2, "start": 15.5, "end": 30.0, "text": "ternyata trik ini sangat berguna", "confidence": 0.9},
        ]
        result = find_segments(segments, min_duration=10, max_duration=60)
        assert len(result) >= 1
        for seg in result:
            assert seg["duration"] >= 10
            assert seg["duration"] <= 60
            assert "startTime" in seg
            assert "endTime" in seg
            assert "text" in seg
            assert "reason" in seg

    def test_short_segments_merged(self):
        segments = [
            {"index": 0, "start": 0.0, "end": 3.0, "text": "Short", "confidence": 0.9},
            {"index": 1, "start": 3.5, "end": 7.0, "text": "Also short", "confidence": 0.9},
            {"index": 2, "start": 7.5, "end": 15.0, "text": "Longer part here", "confidence": 0.9},
        ]
        result = find_segments(segments, min_duration=10, max_duration=60)
        for seg in result:
            assert seg["duration"] >= 10

    def test_long_segments_split_at_max(self):
        segments = [
            {"index": i, "start": float(i * 5), "end": float((i + 1) * 5), "text": f"Part {i}", "confidence": 0.9}
            for i in range(20)
        ]
        result = find_segments(segments, min_duration=10, max_duration=60)
        for seg in result:
            assert seg["duration"] <= 60

    def test_empty_transcript(self):
        result = find_segments([], min_duration=10, max_duration=60)
        assert result == []

    def test_single_long_segment(self):
        segments = [
            {"index": i, "start": float(i * 3), "end": float((i + 1) * 3), "text": f"Word {i}", "confidence": 0.9}
            for i in range(10)
        ]
        result = find_segments(segments, min_duration=10, max_duration=60)
        assert len(result) >= 1

    def test_gap_creates_boundary(self):
        segments = [
            {"index": 0, "start": 0.0, "end": 10.0, "text": "First topic", "confidence": 0.9},
            {"index": 1, "start": 15.0, "end": 30.0, "text": "Second topic after gap", "confidence": 0.9},
        ]
        result = find_segments(segments, min_duration=10, max_duration=60)
        assert len(result) >= 1


class TestSegmentReason:
    def test_question_reason(self):
        seg = {"text": "Kenapa ini penting?"}
        reason = _segment_reason(seg, gap=0.5)
        assert "question" in reason

    def test_hook_phrase_reason(self):
        seg = {"text": "Rahasia yang penting untuk kamu tahu"}
        reason = _segment_reason(seg, gap=0.5)
        assert "hook" in reason.lower()

    def test_topic_shift_reason(self):
        seg = {"text": "Normal text"}
        reason = _segment_reason(seg, gap=3.0)
        assert "shift" in reason.lower()

    def test_continuous_reason(self):
        seg = {"text": "Normal text"}
        reason = _segment_reason(seg, gap=0.5)
        assert "continuous" in reason.lower()


# ── Segment subprocess tests ──


class TestSegmentScript:
    def test_segment_with_valid_transcript(self, tmp_path):
        transcript = {
            "videoId": "test-video",
            "segments": [
                {"index": 0, "start": 0.0, "end": 5.0, "text": "Halo semuanya", "confidence": 0.95},
                {"index": 1, "start": 5.5, "end": 12.0, "text": "hari ini kita bahas rahasia penting", "confidence": 0.91},
                {"index": 2, "start": 12.5, "end": 20.0, "text": "ternyata ada yang tidak banyak orang tahu", "confidence": 0.88},
                {"index": 3, "start": 20.5, "end": 28.0, "text": "ini trik yang sangat berguna", "confidence": 0.90},
                {"index": 4, "start": 28.5, "end": 38.0, "text": "jadi perhatikan baik-baik ya", "confidence": 0.87},
                {"index": 5, "start": 38.5, "end": 50.0, "text": "langkah pertama adalah memahami dasarnya", "confidence": 0.89},
                {"index": 6, "start": 50.5, "end": 65.0, "text": "kemudian langkah kedua mulai praktik", "confidence": 0.92},
                {"index": 7, "start": 65.5, "end": 80.0, "text": "dan langkah ketiga evaluasi hasilnya", "confidence": 0.90},
            ],
        }
        transcript_path = tmp_path / "transcript.json"
        transcript_path.write_text(json.dumps(transcript), encoding="utf-8")

        result = run_script("segment.py", [
            "--transcript", str(transcript_path),
            "--output", str(tmp_path / "segments.json"),
        ])
        assert result.returncode == 0, f"stderr: {result.stderr}"
        output = json.loads(result.stdout)
        assert output["success"] is True

    def test_segment_missing_transcript_arg(self):
        result = run_script("segment.py", [])
        assert result.returncode != 0

    def test_segment_output_file_created(self, tmp_path):
        transcript = {
            "videoId": "test",
            "segments": [
                {"index": 0, "start": 0.0, "end": 15.0, "text": "Test segment", "confidence": 0.9},
            ],
        }
        transcript_path = tmp_path / "transcript.json"
        transcript_path.write_text(json.dumps(transcript), encoding="utf-8")
        output_path = tmp_path / "segments.json"

        result = run_script("segment.py", [
            "--transcript", str(transcript_path),
            "--output", str(output_path),
        ])
        assert result.returncode == 0
        assert output_path.exists()


# ── Score unit tests ──


class TestScoreHookStrength:
    def test_question_and_hook_phrase(self):
        assert score_hook_strength("Rahasia apa yang penting?") == 1.0

    def test_question_only(self):
        assert score_hook_strength("Kenapa bisa begitu?") == 0.7

    def test_hook_phrase_only(self):
        assert score_hook_strength("Rahasia yang harus kamu tahu") == 0.7

    def test_short_sentence(self):
        assert score_hook_strength("Coba perhatikan ini") >= 0.4

    def test_no_hook(self):
        result = score_hook_strength("Jadi kemudian kita lanjutkan pembahasan tentang hal yang sangat panjang ini")
        assert result <= 0.4


class TestScoreKeywordTrigger:
    def test_three_keywords(self):
        assert score_keyword_trigger("rahasia penting dan ternyata bahaya") == 1.0

    def test_two_keywords(self):
        assert score_keyword_trigger("rahasia dan penting saja") == 0.7

    def test_one_keyword(self):
        assert score_keyword_trigger("ini rahasia saja") == 0.4

    def test_zero_keywords(self):
        assert score_keyword_trigger("biasa saja tidak ada yang spesial") == 0.0


class TestScoreNovelty:
    def test_with_numbers(self):
        assert score_novelty("Ada 3 langkah untuk meningkatkan 50% hasil") >= 0.5

    def test_with_proper_nouns(self):
        assert score_novelty("Jakarta memiliki potensi besar") >= 0.5

    def test_with_step_words(self):
        assert score_novelty("Pertama kita mulai, kedua kita lanjut") >= 0.4

    def test_generic(self):
        result = score_novelty("jadi kemudian lanjut saja")
        assert result <= 0.3

    def test_cap_at_one(self):
        result = score_novelty("Ada 3 langkah di Jakarta, pertama kita mulai")
        assert result <= 1.0


class TestScoreClarity:
    def test_short_segment(self):
        assert score_clarity(20, "clear topic") >= 0.9

    def test_medium_segment(self):
        result = score_clarity(35, "moderate topic")
        assert 0.5 <= result <= 0.8

    def test_long_segment(self):
        assert score_clarity(55, "long segment") <= 0.5

    def test_context_word_boost(self):
        without = score_clarity(40, "pembahasan lanjutan")
        with_ctx = score_clarity(40, "jadi intinya begini")
        assert with_ctx >= without


class TestScoreDefaults:
    def test_emotional_energy_default(self):
        assert score_emotional_energy() == 0.5

    def test_pause_structure_default(self):
        assert score_pause_structure() == 0.6

    def test_face_presence_default(self):
        assert score_face_presence() == 0.5

    def test_scene_change_default(self):
        assert score_scene_change() == 0.5

    def test_history_default(self):
        assert score_history() == 0.5


class TestScoreTopicFit:
    def test_strong_match(self):
        assert score_topic_fit("rahasia penting dan solusi", ["rahasia", "penting", "solusi"]) == 1.0

    def test_moderate_match(self):
        result = score_topic_fit("ada rahasia di sini", ["rahasia", "penting", "trik", "solusi", "tip"])
        assert result == 0.7

    def test_no_match(self):
        assert score_topic_fit("biasa saja", ["rahasia", "penting"]) == 0.3

    def test_empty_niche(self):
        assert score_topic_fit("any text", None) == 0.5

    def test_empty_niche_list(self):
        assert score_topic_fit("any text", []) == 0.5


class TestCalcBoosts:
    def test_sharp_question(self):
        assert calc_boosts("Kenapa kok bisa begitu?") >= 0.05

    def test_opinion_conflict(self):
        assert calc_boosts("Tapi sebenarnya beda") >= 0.05

    def test_number_list(self):
        assert calc_boosts("Pertama kita mulai, kedua lanjut") >= 0.03

    def test_emotional_moment(self):
        assert calc_boosts("Wow ini kaget banget") >= 0.05

    def test_no_boost(self):
        assert calc_boosts("biasa saja tidak ada yang spesial") == 0.0

    def test_multiple_boosts(self):
        result = calc_boosts("Kenapa kok bisa begitu? Tapi ternyata berbeda!")
        assert result >= 0.10


class TestCalcPenalties:
    def test_slow_opening(self):
        assert calc_penalties("jadi kemudian lanjut saja", 25) >= 0.08

    def test_too_generic(self):
        assert calc_penalties("jadi", 25) >= 0.05

    def test_no_penalty_for_hook(self):
        result = calc_penalties("Rahasia penting yang harus kamu tahu!", 25)
        assert result < 0.08

    def test_too_much_silence_penalty(self):
        scores = {"pauseStructure": 0.1}
        result = calc_penalties("Rahasia penting!", 25, scores)
        assert result >= PENALTY_CONDITIONS["too_much_silence"]

    def test_no_face_penalty(self):
        scores = {"facePresence": 0.0}
        result = calc_penalties("Rahasia penting!", 25, scores)
        assert result >= PENALTY_CONDITIONS["no_face"]

    def test_no_silence_or_face_penalty_without_scores(self):
        result = calc_penalties("Rahasia penting!", 25)
        assert result == 0.0

    def test_combined_penalties(self):
        scores = {"pauseStructure": 0.1, "facePresence": 0.0}
        result = calc_penalties("jadi kemudian lanjut saja", 25, scores)
        assert result >= PENALTY_CONDITIONS["slow_opening"] + PENALTY_CONDITIONS["too_much_silence"] + PENALTY_CONDITIONS["no_face"]


class TestDetermineTier:
    def test_primary(self):
        assert determine_tier(0.85) == "PRIMARY"
        assert determine_tier(0.80) == "PRIMARY"
        assert determine_tier(1.0) == "PRIMARY"

    def test_backup(self):
        assert determine_tier(0.75) == "BACKUP"
        assert determine_tier(0.65) == "BACKUP"

    def test_skip(self):
        assert determine_tier(0.64) == "SKIP"
        assert determine_tier(0.3) == "SKIP"
        assert determine_tier(0.0) == "SKIP"


class TestScoreSegment:
    def test_full_segment_scoring(self):
        segment = {
            "index": 0,
            "startTime": 0.0,
            "endTime": 25.0,
            "duration": 25.0,
            "text": "Rahasia penting! Kenapa tidak banyak orang tahu? Tapi ternyata ada solusi!",
            "reason": "strong hook",
        }
        result = score_segment(segment, niche_keywords=["rahasia", "penting", "solusi"])
        assert "finalScore" in result
        assert "tier" in result
        assert "scores" in result
        assert result["tier"] in ("PRIMARY", "BACKUP", "SKIP")
        assert result["finalScore"] > 0

    def test_weak_segment(self):
        segment = {
            "index": 0,
            "startTime": 0.0,
            "endTime": 50.0,
            "duration": 50.0,
            "text": "jadi kemudian lanjut saja pembahasan",
            "reason": "generic",
        }
        result = score_segment(segment)
        assert result["tier"] == "SKIP"

    def test_weights_sum_to_one(self):
        assert abs(sum(WEIGHTS.values()) - 1.0) < 0.001


# ── Score subprocess tests ──


class TestScoreScript:
    def test_score_with_valid_segments(self, tmp_path):
        segments = {
            "videoId": "test-video",
            "segments": [
                {"index": 0, "startTime": 0.0, "endTime": 25.0, "duration": 25.0,
                 "text": "Rahasia penting yang tidak banyak orang tahu!", "reason": "hook"},
                {"index": 1, "startTime": 30.0, "endTime": 55.0, "duration": 25.0,
                 "text": "Jadi langkah pertama adalah memahami konsep dasarnya", "reason": "generic"},
            ],
        }
        segments_path = tmp_path / "segments.json"
        segments_path.write_text(json.dumps(segments), encoding="utf-8")
        video_path = tmp_path / "video.mp4"
        video_path.write_bytes(b"fake video content")

        result = run_script("score.py", [
            "--segments", str(segments_path),
            "--video", str(video_path),
            "--niche-keywords", "rahasia,penting,trik,solusi",
        ])
        assert result.returncode == 0, f"stderr: {result.stderr}"
        output = json.loads(result.stdout)
        assert output["success"] is True

    def test_score_missing_segments_arg(self):
        result = run_script("score.py", [])
        assert result.returncode != 0


# ── Transcribe subprocess test (requires faster-whisper) ──


class TestTranscribeScript:
    def test_transcribe_missing_audio_arg(self):
        result = run_script("transcribe.py", [])
        assert result.returncode != 0

    def test_transcribe_nonexistent_audio_file(self):
        result = run_script("transcribe.py", ["--audio", "/nonexistent/file.wav"])
        assert result.returncode != 0

    def test_transcribe_main_with_invalid_audio(self, tmp_path, monkeypatch):
        monkeypatch.setattr(sys, "argv", [
            "transcribe.py",
            "--audio", str(tmp_path / "nonexistent.wav"),
            "--output", str(tmp_path / "out.json"),
        ])
        with pytest.raises(SystemExit) as exc_info:
            from transcribe import main
            main()
        assert exc_info.value.code == 1


class TestSegmentMain:
    def test_segment_main_with_valid_file(self, tmp_path, monkeypatch):
        transcript = {
            "videoId": "test",
            "segments": [
                {"index": 0, "start": 0.0, "end": 15.0, "text": "Test text here", "confidence": 0.9},
            ],
        }
        transcript_path = tmp_path / "transcript.json"
        transcript_path.write_text(json.dumps(transcript), encoding="utf-8")

        monkeypatch.setattr(sys, "argv", [
            "segment.py",
            "--transcript", str(transcript_path),
        ])
        from segment import main
        main()

    def test_segment_main_with_output(self, tmp_path, monkeypatch):
        transcript = {
            "videoId": "test",
            "segments": [
                {"index": 0, "start": 0.0, "end": 15.0, "text": "Test", "confidence": 0.9},
            ],
        }
        transcript_path = tmp_path / "transcript.json"
        transcript_path.write_text(json.dumps(transcript), encoding="utf-8")
        output_path = tmp_path / "output" / "segments.json"

        monkeypatch.setattr(sys, "argv", [
            "segment.py",
            "--transcript", str(transcript_path),
            "--output", str(output_path),
        ])
        from segment import main
        main()
        assert output_path.exists()

    def test_segment_main_bad_transcript(self, tmp_path, monkeypatch):
        monkeypatch.setattr(sys, "argv", [
            "segment.py",
            "--transcript", str(tmp_path / "nonexistent.json"),
        ])
        with pytest.raises(SystemExit) as exc_info:
            from segment import main
            main()
        assert exc_info.value.code == 1


class TestScoreMain:
    def test_score_main_with_valid_file(self, tmp_path, monkeypatch):
        segments = {
            "videoId": "test",
            "segments": [
                {"index": 0, "startTime": 0.0, "endTime": 25.0, "duration": 25.0,
                 "text": "Rahasia penting!", "reason": "hook"},
            ],
        }
        segments_path = tmp_path / "segments.json"
        segments_path.write_text(json.dumps(segments), encoding="utf-8")
        video_path = tmp_path / "video.mp4"
        video_path.write_bytes(b"fake")

        monkeypatch.setattr(sys, "argv", [
            "score.py",
            "--segments", str(segments_path),
            "--video", str(video_path),
        ])
        from score import main
        main()

    def test_score_main_with_niche_keywords(self, tmp_path, monkeypatch):
        segments = {
            "videoId": "test",
            "segments": [
                {"index": 0, "startTime": 0.0, "endTime": 20.0, "duration": 20.0,
                 "text": "Biasa saja", "reason": "generic"},
            ],
        }
        segments_path = tmp_path / "segments.json"
        segments_path.write_text(json.dumps(segments), encoding="utf-8")
        video_path = tmp_path / "video.mp4"
        video_path.write_bytes(b"fake")

        monkeypatch.setattr(sys, "argv", [
            "score.py",
            "--segments", str(segments_path),
            "--video", str(video_path),
            "--niche-keywords", "rahasia,penting",
        ])
        from score import main
        main()

    def test_score_main_bad_segments(self, tmp_path, monkeypatch):
        video_path = tmp_path / "video.mp4"
        video_path.write_bytes(b"fake")

        monkeypatch.setattr(sys, "argv", [
            "score.py",
            "--segments", str(tmp_path / "nonexistent.json"),
            "--video", str(video_path),
        ])
        with pytest.raises(SystemExit) as exc_info:
            from score import main
            main()
        assert exc_info.value.code == 1


# ── Constants validation ──


class TestConstants:
    def test_hook_phrases_not_empty(self):
        assert len(HOOK_PHRASES) > 0

    def test_keyword_triggers_not_empty(self):
        assert len(KEYWORD_TRIGGERS) > 0

    def test_all_weights_present(self):
        expected = {"hookStrength", "keywordTrigger", "novelty", "clarity",
                     "emotionalEnergy", "pauseStructure", "facePresence",
                     "sceneChange", "topicFit", "historyScore"}
        assert set(WEIGHTS.keys()) == expected

    def test_hook_phrases_are_indonesian(self):
        for phrase in HOOK_PHRASES:
            assert isinstance(phrase, str)
            assert len(phrase) > 0

    def test_keyword_triggers_are_indonesian(self):
        for kw in KEYWORD_TRIGGERS:
            assert isinstance(kw, str)
            assert len(kw) > 0


# ── Render tests ──


from render import (
    render_clip,
    find_ffmpeg,
)
from variation import VARIATION_PRESETS


class TestRenderUnit:
    def test_find_ffmpeg_default(self):
        result = find_ffmpeg()
        assert isinstance(result, str)
        assert len(result) > 0

    def test_find_ffmpeg_env_override(self, monkeypatch):
        monkeypatch.setenv("FFMPEG_PATH", "/custom/ffmpeg")
        assert find_ffmpeg() == "/custom/ffmpeg"

    def test_render_clip_nonexistent_video(self, tmp_path):
        with pytest.raises((RuntimeError, FileNotFoundError)):
            render_clip(
                "/nonexistent/video.mp4",
                0.0, 10.0,
                str(tmp_path / "out.mp4"),
                ffmpeg_path="ffmpeg",
            )


class TestRenderScript:
    def test_render_missing_args(self):
        result = run_script("render.py", [])
        assert result.returncode != 0

    def test_render_with_valid_segments_no_video(self, tmp_path):
        segments = {
            "videoId": "test",
            "scoredSegments": [
                {"rank": 1, "tier": "PRIMARY", "startTime": 0.0, "endTime": 10.0,
                 "finalScore": 0.85, "text": "test"},
            ],
        }
        segments_path = tmp_path / "segments.json"
        segments_path.write_text(json.dumps(segments), encoding="utf-8")

        result = run_script("render.py", [
            "--segments", str(segments_path),
            "--video", str(tmp_path / "nonexistent.mp4"),
            "--output-dir", str(tmp_path / "renders"),
        ])
        output = json.loads(result.stdout)
        assert output["success"] is True
        assert output["data"]["failedCount"] >= 1


# ── Subtitle tests ──


from subtitle import (
    build_word_timeline,
    build_subtitle_filter,
    escape_drawtext as subtitle_escape,
)


class TestBuildWordTimeline:
    def test_basic_timeline(self):
        segments = [
            {"start": 0.0, "end": 5.0, "text": "hello world foo bar"},
            {"start": 5.5, "end": 10.0, "text": "test words here now"},
        ]
        words = build_word_timeline(segments, 0.0, 10.0)
        assert len(words) > 0
        for w in words:
            assert "text" in w
            assert "start" in w
            assert "end" in w
            assert w["start"] >= 0

    def test_clips_to_range(self):
        segments = [
            {"start": 0.0, "end": 5.0, "text": "before"},
            {"start": 5.0, "end": 10.0, "text": "within range"},
            {"start": 10.0, "end": 15.0, "text": "after"},
        ]
        words = build_word_timeline(segments, 5.0, 10.0)
        assert len(words) > 0

    def test_empty_segments(self):
        words = build_word_timeline([], 0.0, 10.0)
        assert words == []

    def test_no_overlapping_segments(self):
        segments = [
            {"start": 20.0, "end": 30.0, "text": "far away"},
        ]
        words = build_word_timeline(segments, 0.0, 10.0)
        assert words == []


class TestBuildSubtitleFilter:
    def test_with_words(self):
        words = [
            {"text": "hello", "start": 0.0, "end": 0.5},
            {"text": "world", "start": 0.5, "end": 1.0},
        ]
        result = build_subtitle_filter(words)
        assert "drawtext" in result
        assert "hello" in result

    def test_empty_words(self):
        result = build_subtitle_filter([])
        assert result == "null"

    def test_chunking(self):
        words = [
            {"text": f"w{i}", "start": float(i), "end": float(i + 1)}
            for i in range(12)
        ]
        result = build_subtitle_filter(words, max_chars=5)
        parts = result.split("drawtext")
        assert len(parts) > 2


class TestSubtitleEscape:
    def test_escape_colons(self):
        result = subtitle_escape("test:value")
        assert "\\:" in result

    def test_escape_commas(self):
        result = subtitle_escape("test,value")
        assert "\\," in result

    def test_escape_brackets(self):
        result = subtitle_escape("test[1]")
        assert "\\[" in result
        assert "\\]" in result


class TestSubtitleScript:
    def test_subtitle_missing_args(self):
        result = run_script("subtitle.py", [])
        assert result.returncode != 0


# ── Variation tests ──


from variation import VARIATION_PRESETS, generate_variation


class TestVariationPresets:
    def test_presets_exist(self):
        assert len(VARIATION_PRESETS) >= 3
        assert "zoom_center" in VARIATION_PRESETS
        assert "zoom_top" in VARIATION_PRESETS
        assert "dynamic_crop" in VARIATION_PRESETS

    def test_preset_has_label(self):
        for name, preset in VARIATION_PRESETS.items():
            assert "label" in preset
            assert "vf_extra" in preset

    def test_preset_has_frames_placeholder(self):
        for name, preset in VARIATION_PRESETS.items():
            if "zoompan" in preset["vf_extra"]:
                assert "{frames}" in preset["vf_extra"]


class TestVariationScript:
    def test_variation_missing_args(self):
        result = run_script("variation.py", [])
        assert result.returncode != 0

    def test_variation_no_primary(self, tmp_path):
        segments = {
            "videoId": "test",
            "scoredSegments": [
                {"rank": 1, "tier": "SKIP", "startTime": 0.0, "endTime": 10.0,
                 "finalScore": 0.3, "text": "meh"},
            ],
        }
        segments_path = tmp_path / "segments.json"
        segments_path.write_text(json.dumps(segments), encoding="utf-8")

        result = run_script("variation.py", [
            "--segments", str(segments_path),
            "--video", str(tmp_path / "video.mp4"),
            "--output-dir", str(tmp_path / "variations"),
        ])
        assert result.returncode == 0
        output = json.loads(result.stdout)
        assert output["success"] is True
        assert output["data"]["variationCount"] == 0


# ── Analytics tests ──


from analytics import (
    calc_tier_distribution,
    calc_score_stats,
    calc_duration_stats,
    calc_top_features,
    generate_recommendations,
)


class TestCalcTierDistribution:
    def test_basic_distribution(self):
        segments = [
            {"tier": "PRIMARY"}, {"tier": "PRIMARY"}, {"tier": "BACKUP"}, {"tier": "SKIP"},
        ]
        result = calc_tier_distribution(segments)
        assert result["PRIMARY"] == 2
        assert result["BACKUP"] == 1
        assert result["SKIP"] == 1

    def test_empty(self):
        result = calc_tier_distribution([])
        assert result["PRIMARY"] == 0

    def test_missing_tier(self):
        result = calc_tier_distribution([{"text": "no tier"}])
        assert result["SKIP"] == 1


class TestCalcScoreStats:
    def test_basic_stats(self):
        segments = [{"finalScore": 0.9}, {"finalScore": 0.5}, {"finalScore": 0.3}]
        result = calc_score_stats(segments)
        assert result["min"] == 0.3
        assert result["max"] == 0.9
        assert abs(result["avg"] - 0.5667) < 0.01
        assert result["count"] == 3

    def test_empty(self):
        result = calc_score_stats([])
        assert result["count"] == 0

    def test_single(self):
        segments = [{"finalScore": 0.75}]
        result = calc_score_stats(segments)
        assert result["min"] == result["max"] == result["avg"] == 0.75


class TestCalcDurationStats:
    def test_basic(self):
        segments = [{"duration": 20}, {"duration": 40}, {"duration": 60}]
        result = calc_duration_stats(segments)
        assert result["min"] == 20.0
        assert result["max"] == 60.0
        assert result["avg"] == 40.0

    def test_empty(self):
        result = calc_duration_stats([])
        assert result["min"] == 0


class TestCalcTopFeatures:
    def test_basic(self):
        segments = [
            {"scores": {"hookStrength": 0.8, "keywordTrigger": 0.5, "clarity": 0.9}},
            {"scores": {"hookStrength": 0.6, "keywordTrigger": 0.7, "clarity": 0.3}},
        ]
        result = calc_top_features(segments)
        assert len(result) == 3
        assert result[0]["feature"] == "hookStrength"
        assert abs(result[0]["average"] - 0.7) < 0.01

    def test_empty(self):
        result = calc_top_features([])
        assert result == []

    def test_no_scores_key(self):
        result = calc_top_features([{"text": "no scores"}])
        assert result == []


class TestGenerateRecommendations:
    def test_no_primary(self):
        recs = generate_recommendations({"PRIMARY": 0, "BACKUP": 2, "SKIP": 3}, {"avg": 0.4}, {"avg": 25})
        assert any("No PRIMARY" in r for r in recs)

    def test_many_primary(self):
        recs = generate_recommendations({"PRIMARY": 8, "BACKUP": 2, "SKIP": 1}, {"avg": 0.7}, {"avg": 25})
        assert any("Many PRIMARY" in r for r in recs)

    def test_low_avg_score(self):
        recs = generate_recommendations({"PRIMARY": 2, "BACKUP": 2, "SKIP": 3}, {"avg": 0.3}, {"avg": 25})
        assert any("low" in r.lower() for r in recs)

    def test_long_clips(self):
        recs = generate_recommendations({"PRIMARY": 2, "BACKUP": 2, "SKIP": 1}, {"avg": 0.7}, {"avg": 55})
        assert any("long" in r.lower() for r in recs)

    def test_healthy(self):
        recs = generate_recommendations({"PRIMARY": 2, "BACKUP": 3, "SKIP": 2}, {"avg": 0.6}, {"avg": 30})
        assert any("healthy" in r.lower() for r in recs)


class TestAnalyticsScript:
    def test_analytics_missing_args(self):
        result = run_script("analytics.py", [])
        assert result.returncode != 0

    def test_analytics_with_valid_segments(self, tmp_path):
        segments = {
            "videoId": "test-video",
            "scoredSegments": [
                {"rank": 1, "tier": "PRIMARY", "startTime": 0.0, "endTime": 20.0,
                 "duration": 20.0, "finalScore": 0.85, "text": "Rahasia penting!",
                 "scores": {"hookStrength": 0.9, "keywordTrigger": 0.8, "novelty": 0.7,
                            "clarity": 0.9, "emotionalEnergy": 0.5, "pauseStructure": 0.6,
                            "facePresence": 0.5, "sceneChange": 0.5, "topicFit": 0.7,
                            "historyScore": 0.5, "boostTotal": 0.05, "penaltyTotal": 0.0}},
                {"rank": 2, "tier": "BACKUP", "startTime": 25.0, "endTime": 50.0,
                 "duration": 25.0, "finalScore": 0.70, "text": "Biasa saja",
                 "scores": {"hookStrength": 0.3, "keywordTrigger": 0.0, "novelty": 0.2,
                            "clarity": 0.6, "emotionalEnergy": 0.5, "pauseStructure": 0.6,
                            "facePresence": 0.5, "sceneChange": 0.5, "topicFit": 0.3,
                            "historyScore": 0.5, "boostTotal": 0.0, "penaltyTotal": 0.08}},
            ],
        }
        segments_path = tmp_path / "segments.json"
        segments_path.write_text(json.dumps(segments), encoding="utf-8")

        result = run_script("analytics.py", [
            "--segments", str(segments_path),
            "--output", str(tmp_path / "analytics.json"),
        ])
        assert result.returncode == 0
        output = json.loads(result.stdout)
        assert output["success"] is True
        assert output["data"]["totalSegments"] == 2
        assert output["data"]["tierDistribution"]["PRIMARY"] == 1
        assert output["data"]["tierDistribution"]["BACKUP"] == 1
        assert (tmp_path / "analytics.json").exists()

    def test_analytics_empty_segments(self, tmp_path):
        segments = {"videoId": "test", "scoredSegments": []}
        segments_path = tmp_path / "segments.json"
        segments_path.write_text(json.dumps(segments), encoding="utf-8")

        result = run_script("analytics.py", [
            "--segments", str(segments_path),
        ])
        assert result.returncode == 0
        output = json.loads(result.stdout)
        assert output["success"] is True
        assert output["data"]["totalSegments"] == 0


class TestAnalyticsMain:
    def test_analytics_main_with_file(self, tmp_path, monkeypatch):
        segments = {
            "videoId": "test",
            "scoredSegments": [
                {"rank": 1, "tier": "PRIMARY", "duration": 20.0, "finalScore": 0.85,
                 "text": "test", "scores": {"hookStrength": 0.9, "keywordTrigger": 0.5}},
            ],
        }
        segments_path = tmp_path / "segments.json"
        segments_path.write_text(json.dumps(segments), encoding="utf-8")

        monkeypatch.setattr(sys, "argv", [
            "analytics.py",
            "--segments", str(segments_path),
            "--output", str(tmp_path / "out" / "analytics.json"),
        ])
        from analytics import main
        main()
        assert (tmp_path / "out" / "analytics.json").exists()

    def test_analytics_main_no_output(self, tmp_path, monkeypatch):
        segments = {
            "videoId": "test",
            "scoredSegments": [
                {"rank": 1, "tier": "BACKUP", "duration": 25.0, "finalScore": 0.7,
                 "text": "test", "scores": {"hookStrength": 0.5}},
            ],
        }
        segments_path = tmp_path / "segments.json"
        segments_path.write_text(json.dumps(segments), encoding="utf-8")

        monkeypatch.setattr(sys, "argv", [
            "analytics.py",
            "--segments", str(segments_path),
        ])
        from analytics import main
        main()

    def test_analytics_main_bad_file(self, tmp_path, monkeypatch):
        monkeypatch.setattr(sys, "argv", [
            "analytics.py",
            "--segments", str(tmp_path / "nonexistent.json"),
        ])
        with pytest.raises(SystemExit) as exc_info:
            from analytics import main
            main()
        assert exc_info.value.code == 1


class TestRenderMain:
    def test_render_main_bad_file(self, tmp_path, monkeypatch):
        monkeypatch.setattr(sys, "argv", [
            "render.py",
            "--segments", str(tmp_path / "nonexistent.json"),
            "--video", str(tmp_path / "video.mp4"),
            "--output-dir", str(tmp_path / "renders"),
        ])
        with pytest.raises(SystemExit) as exc_info:
            from render import main
            main()
        assert exc_info.value.code == 1

    def test_render_main_with_segments(self, tmp_path, monkeypatch):
        segments = {
            "videoId": "test",
            "scoredSegments": [
                {"rank": 1, "tier": "SKIP", "startTime": 0.0, "endTime": 10.0,
                 "finalScore": 0.3, "text": "test"},
            ],
        }
        segments_path = tmp_path / "segments.json"
        segments_path.write_text(json.dumps(segments), encoding="utf-8")

        monkeypatch.setattr(sys, "argv", [
            "render.py",
            "--segments", str(segments_path),
            "--video", str(tmp_path / "nonexistent.mp4"),
            "--output-dir", str(tmp_path / "renders"),
            "--tiers", "PRIMARY",
        ])
        from render import main
        main()

    def test_render_main_graceful_failure(self, tmp_path, monkeypatch):
        segments = {
            "videoId": "test",
            "scoredSegments": [
                {"rank": 1, "tier": "PRIMARY", "startTime": 0.0, "endTime": 5.0,
                 "finalScore": 0.9, "text": "test"},
            ],
        }
        segments_path = tmp_path / "segments.json"
        segments_path.write_text(json.dumps(segments), encoding="utf-8")

        monkeypatch.setattr(sys, "argv", [
            "render.py",
            "--segments", str(segments_path),
            "--video", str(tmp_path / "nonexistent.mp4"),
            "--output-dir", str(tmp_path / "renders"),
        ])
        from render import main
        main()


class TestSubtitleMain:
    def test_subtitle_main_bad_file(self, tmp_path, monkeypatch):
        monkeypatch.setattr(sys, "argv", [
            "subtitle.py",
            "--transcript", str(tmp_path / "nonexistent.json"),
            "--segments", str(tmp_path / "nonexistent2.json"),
            "--render-dir", str(tmp_path / "renders"),
            "--output-dir", str(tmp_path / "exports"),
        ])
        with pytest.raises(SystemExit) as exc_info:
            from subtitle import main
            main()
        assert exc_info.value.code == 1

    def test_subtitle_main_with_files_but_no_renders(self, tmp_path, monkeypatch):
        transcript = {
            "videoId": "test",
            "segments": [
                {"start": 0.0, "end": 5.0, "text": "hello world"},
            ],
        }
        segments = {
            "videoId": "test",
            "scoredSegments": [
                {"rank": 1, "tier": "PRIMARY", "startTime": 0.0, "endTime": 5.0,
                 "finalScore": 0.9, "text": "hello world"},
            ],
        }
        transcript_path = tmp_path / "transcript.json"
        transcript_path.write_text(json.dumps(transcript), encoding="utf-8")
        segments_path = tmp_path / "segments.json"
        segments_path.write_text(json.dumps(segments), encoding="utf-8")

        monkeypatch.setattr(sys, "argv", [
            "subtitle.py",
            "--transcript", str(transcript_path),
            "--segments", str(segments_path),
            "--render-dir", str(tmp_path / "renders"),
            "--output-dir", str(tmp_path / "exports"),
        ])
        from subtitle import main
        main()


class TestVariationMain:
    def test_variation_main_bad_file(self, tmp_path, monkeypatch):
        monkeypatch.setattr(sys, "argv", [
            "variation.py",
            "--segments", str(tmp_path / "nonexistent.json"),
            "--video", str(tmp_path / "video.mp4"),
            "--output-dir", str(tmp_path / "variations"),
        ])
        with pytest.raises(SystemExit) as exc_info:
            from variation import main
            main()
        assert exc_info.value.code == 1

    def test_variation_main_with_primary_no_video(self, tmp_path, monkeypatch):
        segments = {
            "videoId": "test",
            "scoredSegments": [
                {"rank": 1, "tier": "PRIMARY", "startTime": 0.0, "endTime": 10.0,
                 "finalScore": 0.9, "text": "test"},
            ],
        }
        segments_path = tmp_path / "segments.json"
        segments_path.write_text(json.dumps(segments), encoding="utf-8")

        monkeypatch.setattr(sys, "argv", [
            "variation.py",
            "--segments", str(segments_path),
            "--video", str(tmp_path / "nonexistent.mp4"),
            "--output-dir", str(tmp_path / "variations"),
        ])
        from variation import main
        main()
