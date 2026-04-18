# AI Viral Clipper — Plan untuk Penggunaan Pribadi (Local-Only)

## 1. Tujuan

Project ini dibuat untuk kebutuhan pribadi:
- input video dari YouTube URL atau file lokal
- otomatis cari bagian menarik
- potong jadi clip pendek
- ubah ke format vertikal 9:16
- tambah subtitle
- simpan hasilnya ke folder lokal
- review manual sebelum upload ke TikTok

Fokus utama:
1. hemat biaya
2. gampang dibangun sendiri
3. cukup bagus untuk produksi konten pribadi
4. tidak perlu infra cloud dulu

---

## 2. Scope yang Realistis

### Yang dikerjakan
- download video
- ekstrak audio
- transkrip otomatis
- deteksi kandidat clip
- scoring clip
- render vertical video
- subtitle otomatis
- simpan hasil ke disk lokal
- UI sederhana untuk preview dan pilih clip

### Yang tidak dikerjakan dulu
- multi-user SaaS
- billing
- auto-scaling cloud
- analytics kompleks
- auto publish massal
- fitur enterprise

---

## 3. Target Workflow

### Alur kerja pribadi
1. paste URL video atau upload file
2. sistem download / baca file
3. sistem transkrip audio
4. sistem cari segmen menarik
5. sistem kasih skor ke tiap segmen
6. sistem generate beberapa clip terbaik
7. kamu preview hasilnya
8. kamu edit ringan kalau perlu
9. kamu export final clip
10. kamu upload manual ke TikTok

---

## 4. Arsitektur Lokal

```text
[Web UI / Desktop UI]
        |
        v
[Local API Backend]
        |
        v
[Local Job Queue]
        |
        +-------------------+
        |                   |
        v                   v
[Media Processor]   [AI Analyzer]
        |                   |
        +---------+---------+
                  |
                  v
           [Clip Renderer]
                  |
                  v
            [Local Storage]
                  |
                  v
            [Preview / Export]
```

---

## 5. Komponen Sistem

## 5.1 UI / Frontend
Fungsi:
- input URL / upload file
- lihat status job
- lihat daftar clip hasil scoring
- preview video
- pilih mana yang disimpan
- download hasil akhir

Kalau mau sederhana:
- web app lokal
- atau desktop app
- atau web local saja di `localhost`

---

## 5.2 Backend API
Fungsi:
- terima request user
- simpan metadata
- buat job processing
- expose status pipeline
- ambil daftar clip / hasil render

Endpoint yang dibutuhkan:
- `POST /import`
- `POST /process`
- `GET /jobs/{id}`
- `GET /videos/{id}`
- `GET /clips/{id}`
- `POST /clips/{id}/render`
- `POST /clips/{id}/export`

---

## 5.3 Job Queue Lokal
Fungsi:
- jalankan proses panjang secara async
- urutkan job
- retry kalau gagal

Pilihan sederhana:
- in-memory queue untuk MVP kecil
- Redis queue kalau mau lebih rapi

---

## 5.4 Media Ingestion
Fungsi:
- download video dari URL
- baca file lokal
- cek format
- ekstrak info dasar

Tools:
- `yt-dlp`
- `ffmpeg`

Output:
- raw video
- audio track
- metadata

---

## 5.5 Transcription Engine
Fungsi:
- speech-to-text
- kasih timestamp
- hasil transcript jadi bahan scoring

Tools:
- Whisper
- faster-whisper

Output:
- kalimat + waktu mulai/selesai
- confidence score per segmen

---

## 5.6 Segment Finder
Fungsi:
- potong transcript jadi kandidat segmen
- cari jeda
- cari topik berubah
- cari kalimat yang terasa kuat

Target segment:
- 10–60 detik
- bisa diekspansi atau dipendekkan

---

## 5.7 Scoring Engine
Fungsi:
- kasih nilai pada tiap segmen
- urutkan dari yang paling menarik

Score dipakai untuk:
- pilih clip terbaik
- buang clip yang lemah
- tentukan apakah perlu variasi

---

## 5.8 Renderer
Fungsi:
- potong video
- ubah ke 9:16
- center/crop ke pembicara
- tambahkan subtitle
- export mp4

Tools:
- FFmpeg
- OpenCV
- subtitle renderer sederhana

---

## 5.9 Local Storage
Struktur folder contoh:

```text
project/
  input/
  raw/
  audio/
  transcripts/
  segments/
  clips/
  renders/
  exports/
  logs/
```

---

## 6. Sistem Desain yang Cocok untuk Lokal

## 6.1 Minimal Architecture
Kalau mau cepat jadi, cukup 1 backend + 1 worker.

```text
[UI]
  |
  v
[Backend API]
  |
  v
[Worker]
  |
  v
[FFmpeg + Whisper + Storage]
```

### Kelebihan
- paling gampang dibangun
- debug simpel
- cocok untuk personal use

### Kekurangan
- kalau job berat, UI bisa terasa lambat
- semua proses numpuk di satu mesin

---

## 6.2 Architecture yang Lebih Rapi
Kalau mau tetap lokal tapi lebih enak dipelihara:

```text
[UI]
  |
  v
[API Server] ---> [SQLite/PostgreSQL]
  |
  v
[Queue]
  |
  +--> [Transcription Worker]
  +--> [Analysis Worker]
  +--> [Render Worker]
```

### Kelebihan
- lebih modular
- gampang dipisah kalau nanti mau upgrade
- stabil untuk banyak video

### Kekurangan
- setup lebih banyak
- sedikit lebih kompleks dari MVP sederhana

---

## 7. Scoring Formula untuk Penggunaan Pribadi

Tujuan scoring:
- cari clip yang paling mungkin menarik
- bukan model ilmiah sempurna
- cukup bagus untuk memilah 20 clip jadi top 5

### 7.1 Feature yang dipakai
- hook strength
- keyword trigger
- novelty
- clarity
- emotional energy
- pause structure
- face presence
- scene change
- topic fit
- history score

---

## 7.2 Formula Dasar

```text
final_score =
  0.20 * hook_strength +
  0.10 * keyword_trigger +
  0.10 * novelty +
  0.10 * clarity +
  0.10 * emotional_energy +
  0.08 * pause_structure +
  0.10 * face_presence +
  0.08 * scene_change +
  0.12 * topic_fit +
  0.12 * history_score
```

Total = 1.00

---

## 7.3 Penjelasan Feature

### hook_strength
Seberapa kuat kalimat pembuka di 3 detik pertama.

### keyword_trigger
Ada kata yang memancing perhatian seperti:
- rahasia
- penting
- tidak banyak orang tahu
- kesalahan
- ternyata

### novelty
Seberapa unik / tidak generik isi clip.

### clarity
Seberapa cepat konteks clip bisa dipahami.

### emotional_energy
Nada bicara, intensitas, dan dinamika suara.

### pause_structure
Clip yang terlalu banyak hening akan turun nilainya.

### face_presence
Kalau wajah pembicara terlihat jelas, biasanya lebih engaging.

### scene_change
Kalau ada perubahan visual yang cukup aktif, clip terasa hidup.

### topic_fit
Seberapa cocok dengan niche kamu.

### history_score
Berdasarkan performa clip sebelumnya dari topik serupa.

---

## 7.4 Rule Boost / Penalty

### Boost
- ada pertanyaan tajam di awal → +0.05
- ada konflik opini → +0.05
- ada angka / list / step-by-step → +0.03
- ada momen emosional → +0.05

### Penalty
- opening terlalu lambat → -0.08
- banyak diam → -0.07
- terlalu umum → -0.05
- wajah tidak terlihat sama sekali → -0.04

---

## 8. Clip Selection Logic

Contoh threshold:

- `score >= 0.80` → clip utama
- `0.65 - 0.79` → clip cadangan
- `< 0.65` → skip

Kalau kamu mau full auto:
- sistem tetap generate semua
- tapi hanya top clip yang otomatis disimpan ke folder `exports`

---

## 9. Output Format

### Default Output
- 1080 x 1920
- mp4
- subtitle burned in
- durasi 15–60 detik

### Optional Output
- versi tanpa subtitle
- versi dengan style subtitle berbeda
- versi lebih agresif / lebih clean

---

## 10. Rekomendasi Stack untuk Local Only

## Backend
- Java Spring Boot

## Worker / Processing
- Python untuk AI pipeline

## Transcription
- faster-whisper

## Video processing
- FFmpeg
- OpenCV

## Storage
- lokal SSD

## DB
- SQLite untuk MVP
- PostgreSQL kalau mau lebih rapi

## UI
- Next.js atau React
- atau lokal web UI sederhana

---

## 11. Development Phases

## Phase 1 — MVP Lokal
Target:
- import video
- transkrip
- cari segmen
- render clip
- simpan hasil

Fokus:
- jalan dulu, bukan sempurna

---

## Phase 2 — Scoring & Sorting
Target:
- kasih score otomatis
- pilih top clip
- tampilkan ranking ke UI

---

## Phase 3 — Subtitle & Style
Target:
- subtitle otomatis
- keyword highlight
- style template

---

## Phase 4 — Quality Improvements
Target:
- face tracking
- better crop
- better hook detection
- better silence trimming

---

## 12. Perkiraan Resource Lokal

Kalau dijalankan di PC pribadi:
- CPU cukup kuat untuk processing ringan
- GPU sangat membantu untuk transkrip dan render tertentu
- SSD penting karena video itu berat
- RAM 16 GB masih bisa, 32 GB lebih enak

---

## 13. Estimasi Biaya Lokal

### Biaya awal
- kalau pakai PC sendiri: nyaris nol
- kalau beli storage tambahan: tergantung kebutuhan
- kalau upgrade RAM / SSD: opsional

### Biaya bulanan
- listrik
- internet
- storage backup

Biasanya jauh lebih murah daripada cloud, terutama kalau:
- kamu proses video sendiri
- volume tidak terlalu besar
- masih tahap eksperimen

---

## 14. Risiko di Local Setup

- job render bisa berat
- storage cepat penuh
- proses lama kalau source banyak
- kalau PC mati, job berhenti
- backup harus disiplin

---

## 15. Strategi Operasional

Supaya enak dipakai pribadi:
- batch proses malam hari
- simpan source dan hasil di folder terstruktur
- hapus file temp otomatis
- tandai clip yang sudah reviewed
- buat preset style untuk niche berbeda

---

## 16. Saran Build Order

1. input video
2. download / load file
3. transcribe
4. segmentasi
5. scoring
6. render vertical
7. subtitle
8. preview UI
9. export
10. refining scoring

---

## 17. Penutup

Versi local-only ini cocok kalau tujuanmu:
- build cepat
- biaya rendah
- dipakai sendiri
- validasi workflow dulu

Kalau versi ini sudah stabil, baru nanti bisa dipindah ke server atau dipecah jadi beberapa service.
