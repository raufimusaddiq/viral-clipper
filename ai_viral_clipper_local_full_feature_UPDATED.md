# AI Viral Clipper — Local Full Feature Plan (UPDATED: Hardware & STT)

## 1. Hardware Compatibility (Your PC)

### Spec
- CPU: Ryzen 5 7500F
- GPU: RTX 4060 (8GB VRAM)
- RAM: 32GB

### Verdict
System kamu **lebih dari cukup** untuk menjalankan full pipeline secara lokal.

### Estimasi Performa

| Task | Estimasi |
|------|--------|
| Speech-to-text (10 min video) | 1–3 menit |
| Speech-to-text (1 jam video) | 8–15 menit |
| Clip rendering | 1–2x real-time |
| Multi clip batch | Parallel (GPU + CPU split) |

### Bottleneck Nyata
- FFmpeg rendering
- Disk I/O (SSD penting)
- Subtitle rendering
- Face tracking

---

## 2. Speech-to-Text (GRATIS & LOCAL)

## 2.1 Recommended: faster-whisper

### Kenapa
- 3–4x lebih cepat dari Whisper biasa
- GPU acceleration (RTX 4060 cocok)
- support quantization
- tetap akurat

### Setup Recommendation
- model: `medium` (default aman)
- optional: `large-v2 (quantized)` kalau mau lebih akurat

---

## 2.2 Alternative

### Whisper (original)
- lebih lambat
- akurasi tinggi

### whisper.cpp
- ringan
- bisa tanpa GPU
- akurasi sedikit lebih rendah

---

## 2.3 Final Recommendation

Gunakan:
- faster-whisper
- GPU enabled
- model medium

---

## 3. Full Feature Architecture (Local-First)

Tetap menggunakan arsitektur production-grade:

- API Backend
- Job Queue
- Worker (multi-stage)
- Database (PostgreSQL)
- Storage abstraction
- Analytics engine
- Publisher layer

Semua berjalan di lokal, tapi siap dipindah ke cloud.

---

## 4. Pipeline (Updated with STT)

Input Video
 → Ingestion
 → Audio Extraction
 → Speech-to-Text (faster-whisper)
 → Transcript Processing
 → Segment Builder
 → Scoring Engine
 → Clip Generator
 → Subtitle Engine
 → Variation Engine
 → Export / Publish
 → Analytics

---

## 5. STT Integration Design

### Service: Transcription Worker

Input:
- audio.wav

Process:
- load model once (cache di memory)
- batch processing jika banyak video

Output:
[
  {
    "start": 1.2,
    "end": 3.4,
    "text": "ini penting banget"
  }
]

---

## 6. Optimization Strategy

### Jangan lakukan:
- pakai model terbesar langsung
- run semua di CPU

### Lakukan:
- GPU untuk STT
- CPU untuk FFmpeg
- parallel job via queue

---

## 7. Resource Strategy

### GPU (RTX 4060)
- STT inference
- optional video processing

### CPU
- FFmpeg rendering
- subtitle burn-in

### RAM
- caching model
- buffer video

### Disk
- SSD wajib
- cleanup temp files

---

## 8. Local vs Cloud (Updated Insight)

### Local
- cocok untuk 10–50 video/hari
- biaya rendah
- kontrol penuh

### Cloud
- cocok untuk scaling
- biaya meningkat
- parallel massive

---

## 9. Final Conclusion

Dengan setup kamu:

- Bisa full auto pipeline
- Bisa STT cepat (real-time atau lebih cepat)
- Bisa generate banyak clip
- Bisa jalan full feature system

Ini sudah cukup untuk:
- MVP serius
- bahkan small-scale production

---

## 10. Next Step Recommendation

1. Setup faster-whisper
2. Build transcription service
3. Integrasi ke pipeline segment + scoring
4. Test dengan 5–10 video
5. Evaluasi hasil clip
