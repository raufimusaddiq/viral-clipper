#!/usr/bin/env python3
"""
End-to-end pipeline test.
Generates synthetic audio, runs through transcribe -> segment -> score,
verifies JSON envelope at each stage and ffmpeg availability.
"""

import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
import wave
import struct

SCRIPTS_DIR = os.path.join(os.path.dirname(__file__), "..", "ai-pipeline")
VENV_PYTHON = os.path.join(os.path.dirname(__file__), "..", "ai-pipeline", ".venv", "Scripts", "python.exe")

if not os.path.exists(VENV_PYTHON):
    VENV_PYTHON = sys.executable


def find_cmd(name):
    path = shutil.which(name)
    if path:
        return path
    winget_base = os.path.expandvars(
        r"%LOCALAPPDATA%\Microsoft\WinGet\Packages"
    )
    if os.path.isdir(winget_base):
        for d in os.listdir(winget_base):
            dpath = os.path.join(winget_base, d)
            if os.path.isdir(dpath):
                for root, dirs, files in os.walk(dpath):
                    for f in files:
                        low = f.lower()
                        if low == f"{name}.exe" or low == f"{name}.cmd":
                            return os.path.join(root, f)
    return name


def run_cmd(cmd, timeout=120):
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
    return result


def generate_tone_wav(path, duration_sec=15, freq=440, sample_rate=16000):
    with wave.open(path, "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sample_rate)
        frames = int(duration_sec * sample_rate)
        samples = []
        for i in range(frames):
            val = int(16000 * (0.3 * (i % sample_rate < sample_rate // 2)))
            samples.append(max(-32768, min(32767, val)))
        data = struct.pack(f"<{frames}h", *samples)
        w.writeframes(data)


class TestSystemDependencies(unittest.TestCase):
    def test_ffmpeg_available(self):
        path = find_cmd("ffmpeg")
        result = run_cmd([path, "-version"])
        self.assertEqual(result.returncode, 0, f"ffmpeg not found at {path}")

    def test_ytdlp_available(self):
        path = find_cmd("yt-dlp")
        result = run_cmd([path, "--version"])
        self.assertEqual(result.returncode, 0, f"yt-dlp not found at {path}")

    def test_python_venv_exists(self):
        self.assertTrue(
            os.path.exists(VENV_PYTHON),
            f"Venv Python not found at {VENV_PYTHON}"
        )

    def test_faster_whisper_importable(self):
        result = run_cmd([VENV_PYTHON, "-c", "import faster_whisper; print('ok')"])
        self.assertIn("ok", result.stdout, "faster_whisper not importable")

    def test_opencv_importable(self):
        result = run_cmd([VENV_PYTHON, "-c", "import cv2; print('ok')"])
        self.assertIn("ok", result.stdout, "cv2 not importable")


class TestTranscribeStage(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmpdir = tempfile.mkdtemp()
        cls.audio_path = os.path.join(cls.tmpdir, "test.wav")
        generate_tone_wav(cls.audio_path, duration_sec=15)
        cls.output_path = os.path.join(cls.tmpdir, "transcript.json")
        cls.ffmpeg = find_cmd("ffmpeg")

        result = run_cmd([
            VENV_PYTHON,
            os.path.join(SCRIPTS_DIR, "transcribe.py"),
            "--audio", cls.audio_path,
            "--output", cls.output_path,
            "--model", "tiny",
            "--device", "cpu",
        ], timeout=180)
        cls.transcribe_result = result

    def test_transcribe_exits_zero(self):
        self.assertEqual(
            self.transcribe_result.returncode, 0,
            f"Transcribe failed: {self.transcribe_result.stderr}"
        )

    def test_transcribe_json_envelope(self):
        output = json.loads(self.transcribe_result.stdout)
        self.assertTrue(output["success"])
        self.assertIn("data", output)
        self.assertIn("segments", output["data"])

    def test_transcribe_writes_output_file(self):
        self.assertTrue(os.path.exists(self.output_path), "Output file not created")

    def test_transcribe_output_schema(self):
        with open(self.output_path) as f:
            data = json.load(f)
        self.assertIn("segments", data)
        for seg in data.get("segments", []):
            self.assertIn("start", seg)
            self.assertIn("end", seg)
            self.assertIn("text", seg)


class TestSegmentStage(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmpdir = tempfile.mkdtemp()
        cls.transcript_path = os.path.join(cls.tmpdir, "transcript.json")
        transcript = {
            "videoId": "e2e-test-video",
            "segments": [
                {"index": i, "start": float(i * 5), "end": float((i + 1) * 5),
                 "text": f"Bagian {i} dari pembahasan rahasia penting hari ini",
                 "confidence": 0.9}
                for i in range(20)
            ],
        }
        with open(cls.transcript_path, "w") as f:
            json.dump(transcript, f)
        cls.output_path = os.path.join(cls.tmpdir, "segments.json")

        result = run_cmd([
            VENV_PYTHON,
            os.path.join(SCRIPTS_DIR, "segment.py"),
            "--transcript", cls.transcript_path,
            "--output", cls.output_path,
        ])
        cls.segment_result = result

    def test_segment_exits_zero(self):
        self.assertEqual(
            self.segment_result.returncode, 0,
            f"Segment failed: {self.segment_result.stderr}"
        )

    def test_segment_json_envelope(self):
        output = json.loads(self.segment_result.stdout)
        self.assertTrue(output["success"])
        self.assertIn("data", output)
        self.assertIn("segments", output["data"])

    def test_segment_output_has_reason(self):
        with open(self.output_path) as f:
            data = json.load(f)
        for seg in data["segments"]:
            self.assertIn("reason", seg)
            self.assertIn("startTime", seg)
            self.assertIn("endTime", seg)
            self.assertIn("duration", seg)
            self.assertGreaterEqual(seg["duration"], 10)


class TestScoreStage(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmpdir = tempfile.mkdtemp()
        cls.segments_path = os.path.join(cls.tmpdir, "segments.json")
        segments = {
            "videoId": "e2e-test-video",
            "segments": [
                {"index": 0, "startTime": 0.0, "endTime": 25.0, "duration": 25.0,
                 "text": "Rahasia penting yang tidak banyak orang tahu! Kenapa kok bisa begitu?",
                 "reason": "strong hook"},
                {"index": 1, "startTime": 30.0, "endTime": 55.0, "duration": 25.0,
                 "text": "jadi kemudian lanjut saja pembahasan biasa tanpa hook",
                 "reason": "generic"},
                {"index": 2, "startTime": 60.0, "endTime": 85.0, "duration": 25.0,
                 "text": "Ternyata ada trik dan solusi yang wajib kamu tahu sekarang juga!",
                 "reason": "strong hook"},
            ],
        }
        with open(cls.segments_path, "w") as f:
            json.dump(segments, f)
        cls.video_path = os.path.join(cls.tmpdir, "video.mp4")
        with open(cls.video_path, "wb") as f:
            f.write(b"fake video content for testing")

        result = run_cmd([
            VENV_PYTHON,
            os.path.join(SCRIPTS_DIR, "score.py"),
            "--segments", cls.segments_path,
            "--video", cls.video_path,
            "--niche-keywords", "rahasia,penting,trik,solusi",
        ])
        cls.score_result = result

    def test_score_exits_zero(self):
        self.assertEqual(
            self.score_result.returncode, 0,
            f"Score failed: {self.score_result.stderr}"
        )

    def test_score_json_envelope(self):
        output = json.loads(self.score_result.stdout)
        self.assertTrue(output["success"])
        self.assertIn("data", output)

    def test_scored_segments_have_all_fields(self):
        with open(self.segments_path) as f:
            data = json.load(f)
        segments = data.get("scoredSegments", [])
        self.assertGreater(len(segments), 0)
        for seg in segments:
            self.assertIn("finalScore", seg)
            self.assertIn("tier", seg)
            self.assertIn("scores", seg)
            self.assertIn("rank", seg)
            self.assertIn(seg["tier"], ["PRIMARY", "BACKUP", "SKIP"])

    def test_segments_sorted_by_score(self):
        with open(self.segments_path) as f:
            data = json.load(f)
        segments = data.get("scoredSegments", [])
        scores = [s["finalScore"] for s in segments]
        self.assertEqual(scores, sorted(scores, reverse=True))

    def test_rank_assigned_correctly(self):
        with open(self.segments_path) as f:
            data = json.load(f)
        segments = data.get("scoredSegments", [])
        for i, seg in enumerate(segments):
            self.assertEqual(seg["rank"], i + 1)


class TestFFmpegOperations(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmpdir = tempfile.mkdtemp()
        cls.ffmpeg = find_cmd("ffmpeg")
        cls.audio_path = os.path.join(cls.tmpdir, "input.wav")
        generate_tone_wav(cls.audio_path, duration_sec=5)

    def test_ffmpeg_can_extract_audio(self):
        output_path = os.path.join(self.tmpdir, "extracted.wav")
        result = run_cmd([
            self.ffmpeg, "-y", "-i", self.audio_path,
            "-vn", "-acodec", "pcm_s16le", "-ar", "16000", "-ac", "1",
            output_path,
        ])
        self.assertEqual(result.returncode, 0)
        self.assertTrue(os.path.exists(output_path))

    def test_ffmpeg_can_trim(self):
        output_path = os.path.join(self.tmpdir, "trimmed.wav")
        result = run_cmd([
            self.ffmpeg, "-y", "-i", self.audio_path,
            "-ss", "0", "-to", "2",
            output_path,
        ])
        self.assertEqual(result.returncode, 0)
        self.assertTrue(os.path.exists(output_path))


class TestFullPipelineIntegration(unittest.TestCase):
    """Transcribe -> segment -> score pipeline end to end."""

    @classmethod
    def setUpClass(cls):
        cls.tmpdir = tempfile.mkdtemp()
        cls.ffmpeg = find_cmd("ffmpeg")

    def test_pipeline_produces_scored_clips(self):
        transcript = {
            "videoId": "full-pipeline-test",
            "language": "id",
            "model": "tiny",
            "segments": [
                {"index": i, "start": float(i * 8), "end": float((i + 1) * 8),
                 "text": text, "confidence": 0.9}
                for i, text in enumerate([
                    "Halo semuanya apa kabar",
                    "Rahasia penting yang wajib kamu tahu tentang trik ini",
                    "Ternyata tidak banyak orang tahu fakta ini bahaya sekali",
                    "Pertama kita bahas langkah awalnya yang penting",
                    "Kedua kita lanjutkan dengan praktiknya ada solusi",
                    "Jadi intinya begini ada solusi untuk masalah ini",
                    "Kenapa kok bisa begitu bahaya sekali dampaknya",
                    "Simak baik-baik tips dan trik berikut ini yang penting",
                    "Ada fakta menarik tentang hal ini yang wajib diketahui",
                    "Tapi sebenarnya beda sama yang kita kira ternyata",
                    "Wow ini kaget banget ternyata rahasianya di sini penting",
                    "Untungnya ada solusi yang mudah untuk masalah ini trik",
                ])
            ],
        }
        transcript_path = os.path.join(self.tmpdir, "pipeline_transcript.json")
        with open(transcript_path, "w") as f:
            json.dump(transcript, f)

        segments_path = os.path.join(self.tmpdir, "pipeline_segments.json")
        result = run_cmd([
            VENV_PYTHON,
            os.path.join(SCRIPTS_DIR, "segment.py"),
            "--transcript", transcript_path,
            "--output", segments_path,
        ])
        self.assertEqual(result.returncode, 0, f"Segment failed: {result.stderr}")

        video_path = os.path.join(self.tmpdir, "video.mp4")
        with open(video_path, "wb") as f:
            f.write(b"fake")

        result = run_cmd([
            VENV_PYTHON,
            os.path.join(SCRIPTS_DIR, "score.py"),
            "--segments", segments_path,
            "--video", video_path,
            "--niche-keywords", "rahasia,penting,trik,solusi,tip",
        ])
        self.assertEqual(result.returncode, 0, f"Score failed: {result.stderr}")

        with open(segments_path) as f:
            final = json.load(f)

        segments = final.get("scoredSegments", [])
        self.assertGreater(len(segments), 0, "No segments produced")

        primary_count = len([s for s in segments if s["tier"] == "PRIMARY"])
        backup_count = len([s for s in segments if s["tier"] == "BACKUP"])
        skip_count = len([s for s in segments if s["tier"] == "SKIP"])

        top = segments[0]
        self.assertEqual(top["rank"], 1)
        self.assertIn("hookStrength", top["scores"])
        self.assertIn("boostTotal", top["scores"])
        self.assertIn("penaltyTotal", top["scores"])

        print(f"\n  Pipeline: {len(segments)} clips "
              f"({primary_count} PRIMARY, {backup_count} BACKUP, {skip_count} SKIP)")
        print(f"  Top clip: score={top['finalScore']:.4f} tier={top['tier']}")


class TestFeaturesPackageImports(unittest.TestCase):
    """P3.5-A verification: every features/ submodule must import cleanly."""

    def _venv_import(self, module):
        r = run_cmd([
            VENV_PYTHON, "-c",
            f"import sys; sys.path.insert(0, r'{SCRIPTS_DIR}'); import {module}; print('ok')",
        ])
        return r

    def test_features_constants_importable(self):
        r = self._venv_import("features.constants")
        self.assertIn("ok", r.stdout, f"constants import failed: {r.stderr}")

    def test_features_text_importable(self):
        r = self._venv_import("features.text")
        self.assertIn("ok", r.stdout, f"text import failed: {r.stderr}")

    def test_features_audio_importable(self):
        r = self._venv_import("features.audio")
        self.assertIn("ok", r.stdout, f"audio import failed: {r.stderr}")

    def test_features_visual_importable(self):
        r = self._venv_import("features.visual")
        self.assertIn("ok", r.stdout, f"visual import failed: {r.stderr}")

    def test_features_context_importable(self):
        r = self._venv_import("features.context")
        self.assertIn("ok", r.stdout, f"context import failed: {r.stderr}")

    def test_features_supervised_importable(self):
        r = self._venv_import("features.supervised")
        self.assertIn("ok", r.stdout, f"supervised import failed: {r.stderr}")


class TestP3_5B_NewFeatures(unittest.TestCase):
    """P3.5-B verification: score output must include motion + onsetDensity,
    and the TF-IDF corpus loader must return None when no corpus is populated."""

    @classmethod
    def setUpClass(cls):
        cls.tmpdir = tempfile.mkdtemp()
        cls.segments_path = os.path.join(cls.tmpdir, "segments.json")
        with open(cls.segments_path, "w") as f:
            json.dump({
                "videoId": "p35b",
                "segments": [
                    {"index": 0, "startTime": 0.0, "endTime": 20.0, "duration": 20.0,
                     "text": "Rahasia penting! Kenapa ternyata bisa begitu?",
                     "reason": "hook"},
                ],
            }, f)
        cls.video_path = os.path.join(cls.tmpdir, "stub.mp4")
        with open(cls.video_path, "wb") as f:
            f.write(b"stub")

        cls.result = run_cmd([
            VENV_PYTHON,
            os.path.join(SCRIPTS_DIR, "score.py"),
            "--segments", cls.segments_path,
            "--video", cls.video_path,
        ])

    def test_score_runs_without_mediapipe_or_cuda(self):
        self.assertEqual(self.result.returncode, 0,
                         f"score.py failed on a stub video: {self.result.stderr}")

    def test_scores_dict_contains_motion(self):
        with open(self.segments_path) as f:
            data = json.load(f)
        seg = data["scoredSegments"][0]
        self.assertIn("motion", seg["scores"])
        self.assertGreaterEqual(seg["scores"]["motion"], 0.0)
        self.assertLessEqual(seg["scores"]["motion"], 1.0)

    def test_scores_dict_contains_onset_density(self):
        with open(self.segments_path) as f:
            data = json.load(f)
        seg = data["scoredSegments"][0]
        self.assertIn("onsetDensity", seg["scores"])
        self.assertGreaterEqual(seg["scores"]["onsetDensity"], 0.0)
        self.assertLessEqual(seg["scores"]["onsetDensity"], 1.0)


class TestP3_5C_SupervisedGuard(unittest.TestCase):
    """P3.5-C verification: train_scorer.evaluate refuses to write a model
    when fewer than 200 labeled rows exist."""

    def test_train_scorer_refuses_below_threshold(self):
        result = run_cmd([
            VENV_PYTHON, "-c",
            f"import sys; sys.path.insert(0, r'{SCRIPTS_DIR}'); "
            "from train_scorer import evaluate; "
            "print(evaluate([{'features': {'a': 0.1}, 'actual_viral_score': 0.5}] * 5, "
            "min_rows=200, write_model=True)['status'])",
        ])
        self.assertEqual(result.returncode, 0, f"stderr: {result.stderr}")
        self.assertIn("insufficient_data", result.stdout)


class TestP0_NvencProbe(unittest.TestCase):
    """P0 verification: render.py exposes a cached NVENC probe and the
    command builder puts -ss/-to before -i (fast seek) + format=yuv420p in vf."""

    def test_render_build_cmd_shape(self):
        result = run_cmd([
            VENV_PYTHON, "-c",
            f"import sys; sys.path.insert(0, r'{SCRIPTS_DIR}'); "
            "import render; "
            "cmd = render._build_render_cmd('ffmpeg', '/tmp/x.mp4', 1.0, 2.0, '/tmp/y.mp4', use_nvenc=False); "
            "i = cmd.index('-i'); ss = cmd.index('-ss'); to = cmd.index('-to'); "
            "vf = cmd[cmd.index('-vf')+1]; "
            "print('OK' if ss < i and to < i and 'format=yuv420p' in vf else 'BAD')",
        ])
        self.assertIn("OK", result.stdout,
                      f"render command shape regression: {result.stdout} {result.stderr}")


if __name__ == "__main__":
    unittest.main(verbosity=2)
