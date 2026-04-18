"""Audio analysis helpers for scoring features."""

import subprocess
import os


def find_ffmpeg():
    return os.environ.get("FFMPEG_PATH", "ffmpeg")


def calculate_rms_energy(audio_path, start_sec, end_sec, ffmpeg_path=None):
    """Calculate RMS energy of audio segment. Returns float 0.0-1.0 relative to max."""
    if ffmpeg_path is None:
        ffmpeg_path = find_ffmpeg()
    duration = end_sec - start_sec
    cmd = [
        ffmpeg_path,
        "-i", audio_path,
        "-ss", str(start_sec),
        "-t", str(duration),
        "-af", "astats=metadata=1:reset=1,ametadata=print:key=lavfi.astats.RMS.level",
        "-f", "null", "-",
    ]
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        lines = result.stderr.split("\n")
        rms_values = []
        for line in lines:
            if "lavfi.astats.RMS.level" in line:
                try:
                    val = float(line.split("=")[-1].strip())
                    if val != float("-inf"):
                        rms_values.append(val)
                except (ValueError, IndexError):
                    pass
        if rms_values:
            avg_rms = sum(rms_values) / len(rms_values)
            return round(min(max((avg_rms + 60) / 60, 0.0), 1.0), 4)
    except (subprocess.TimeoutExpired, FileNotFoundError):
        pass
    return 0.5


def calculate_silence_ratio(audio_path, start_sec, end_sec, threshold_db=-40, ffmpeg_path=None):
    """Calculate ratio of silence to total duration in segment."""
    if ffmpeg_path is None:
        ffmpeg_path = find_ffmpeg()
    duration = end_sec - start_sec
    cmd = [
        ffmpeg_path,
        "-i", audio_path,
        "-ss", str(start_sec),
        "-t", str(duration),
        "-af", f"silencedetect=noise={threshold_db}dB:d=0.3",
        "-f", "null", "-",
    ]
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        output = result.stderr
        silence_ends = []
        silence_starts = []
        for line in output.split("\n"):
            if "silence_start" in line:
                try:
                    val = float(line.split("silence_start:")[1].strip().split()[0])
                    silence_starts.append(val)
                except (ValueError, IndexError):
                    pass
            if "silence_end" in line:
                try:
                    val = float(line.split("silence_end:")[1].strip().split("|")[0].strip())
                    silence_ends.append(val)
                except (ValueError, IndexError):
                    pass
        if silence_starts and silence_ends:
            total_silence = sum(
                end - start
                for start, end in zip(silence_starts, silence_ends)
                if end > start
            )
            return round(min(total_silence / duration, 1.0), 4)
        if silence_starts and not silence_ends:
            remaining = silence_starts[-1]
            last_silence = duration - remaining
            return round(min(last_silence / duration, 1.0), 4) if last_silence > 0 else 0.0
    except (subprocess.TimeoutExpired, FileNotFoundError):
        pass
    return 0.1
