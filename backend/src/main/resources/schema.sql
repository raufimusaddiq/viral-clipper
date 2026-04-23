CREATE TABLE IF NOT EXISTS video (
    id TEXT PRIMARY KEY,
    source_url TEXT,
    source_type TEXT NOT NULL,
    title TEXT,
    duration_sec INTEGER,
    file_path TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS job (
    id TEXT PRIMARY KEY,
    video_id TEXT NOT NULL REFERENCES video(id),
    status TEXT NOT NULL DEFAULT 'QUEUED',
    current_stage TEXT,
    error_message TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS stage_status (
    id TEXT PRIMARY KEY,
    job_id TEXT NOT NULL REFERENCES job(id),
    stage TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    started_at TEXT,
    completed_at TEXT,
    error_message TEXT,
    output_path TEXT,
    UNIQUE(job_id, stage)
);

CREATE TABLE IF NOT EXISTS clip (
    id TEXT PRIMARY KEY,
    video_id TEXT NOT NULL REFERENCES video(id),
    rank_pos INTEGER,
    score REAL,
    tier TEXT NOT NULL,
    title TEXT,
    description TEXT,
    start_time REAL NOT NULL,
    end_time REAL NOT NULL,
    duration_sec REAL NOT NULL,
    text_content TEXT,
    render_status TEXT NOT NULL DEFAULT 'PENDING',
    render_path TEXT,
    export_status TEXT NOT NULL DEFAULT 'PENDING',
    export_path TEXT,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS clip_score (
    id TEXT PRIMARY KEY,
    clip_id TEXT NOT NULL UNIQUE REFERENCES clip(id),
    hook_strength REAL,
    keyword_trigger REAL,
    novelty REAL,
    clarity REAL,
    emotional_energy REAL,
    text_sentiment REAL,
    pause_structure REAL,
    face_presence REAL,
    scene_change REAL,
    topic_fit REAL,
    history_score REAL,
    boost_total REAL DEFAULT 0,
    penalty_total REAL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_job_video ON job(video_id);
CREATE INDEX IF NOT EXISTS idx_job_status ON job(status);
CREATE INDEX IF NOT EXISTS idx_clip_video ON clip(video_id);
CREATE INDEX IF NOT EXISTS idx_clip_tier ON clip(tier);
CREATE INDEX IF NOT EXISTS idx_clip_rank ON clip(rank_pos);
CREATE TABLE IF NOT EXISTS clip_feedback (
    id TEXT PRIMARY KEY,
    clip_id TEXT NOT NULL,
    video_id TEXT NOT NULL,
    features TEXT NOT NULL,
    predicted_score REAL NOT NULL,
    predicted_tier TEXT NOT NULL,
    tiktok_views INTEGER DEFAULT 0,
    tiktok_likes INTEGER DEFAULT 0,
    tiktok_comments INTEGER DEFAULT 0,
    tiktok_shares INTEGER DEFAULT 0,
    tiktok_saves INTEGER DEFAULT 0,
    actual_viral_score REAL,
    posted_at TEXT,
    last_checked TEXT,
    created_at TEXT DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_stage_job ON stage_status(job_id);
CREATE INDEX IF NOT EXISTS idx_feedback_clip ON clip_feedback(clip_id);
CREATE INDEX IF NOT EXISTS idx_feedback_video ON clip_feedback(video_id);

-- Indexes that back the scheduled orphan-job sweep (status + updated_at scan)
-- and the "which stages are running now?" queries.
CREATE INDEX IF NOT EXISTS idx_job_updated_at ON job(updated_at);
CREATE INDEX IF NOT EXISTS idx_stage_status_stage_status ON stage_status(stage, status);

-- Migrations for existing databases
ALTER TABLE clip ADD COLUMN title TEXT;
ALTER TABLE clip ADD COLUMN description TEXT;
ALTER TABLE clip_score ADD COLUMN text_sentiment REAL;

-- Discovery persistence (D1). Candidates surfaced by yt-dlp search/trending/channel
-- live here until the user queues them or skips. job_id is populated once imported
-- so we can later correlate predicted_score with the clip_feedback viral scores.
CREATE TABLE IF NOT EXISTS discovered_video (
    id TEXT PRIMARY KEY,
    youtube_id TEXT NOT NULL,
    url TEXT NOT NULL,
    title TEXT,
    channel TEXT,
    duration_sec INTEGER DEFAULT 0,
    view_count INTEGER,
    upload_date TEXT,
    age_hours REAL,
    source_mode TEXT NOT NULL,
    source_query TEXT,
    relevance_score REAL DEFAULT 0,
    transcript_score REAL,
    predicted_score REAL,
    transcript_sample TEXT,
    status TEXT NOT NULL DEFAULT 'NEW',
    job_id TEXT,
    video_id TEXT,
    discovered_at TEXT NOT NULL,
    enriched_at TEXT,
    UNIQUE(youtube_id)
);

-- Saved searches / monitored channels. last_run_at feeds the scheduled refresh (D5).
CREATE TABLE IF NOT EXISTS discovery_query (
    id TEXT PRIMARY KEY,
    mode TEXT NOT NULL,
    query TEXT NOT NULL,
    channel_url TEXT,
    active INTEGER NOT NULL DEFAULT 1,
    auto_refresh_hours INTEGER NOT NULL DEFAULT 24,
    last_run_at TEXT,
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_discovered_status_score ON discovered_video(status, predicted_score DESC);
CREATE INDEX IF NOT EXISTS idx_discovered_status_relevance ON discovered_video(status, relevance_score DESC);
CREATE INDEX IF NOT EXISTS idx_discovered_youtube_id ON discovered_video(youtube_id);
CREATE INDEX IF NOT EXISTS idx_discovery_query_active ON discovery_query(active, last_run_at);

-- Discovery v2 additions. channel_id lets us group candidates by source channel
-- (foreign key to discovery_channel once that table lands). content_type is a
-- heuristic classification from classify_content_type (PODCAST/TALKSHOW/etc).
-- is_likely_clipped flags re-upload/compilation videos so UI can demote them.
-- speech_density_wpm is computed from transcript length / duration and
-- distinguishes "podcast" (~140 wpm) from "music video" (~20 wpm).
-- continue-on-error: true in application.yml swallows duplicate-column errors
-- on re-run.
ALTER TABLE discovered_video ADD COLUMN channel_id TEXT;
ALTER TABLE discovered_video ADD COLUMN content_type TEXT;
ALTER TABLE discovered_video ADD COLUMN speech_density_wpm REAL;
ALTER TABLE discovered_video ADD COLUMN is_likely_clipped INTEGER DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_discovered_channel ON discovered_video(channel_id);
CREATE INDEX IF NOT EXISTS idx_discovered_content_type ON discovered_video(content_type);

-- Discovery v2 Phase 2: tracked channels. trust_score is an EMA updated by
-- the daily trust-feedback job (Phase 3) from clip_feedback.actual_viral_score;
-- seeded at 0.5 for new channels. poll_cadence_hours is adjusted by trust_score
-- tier: 6h (high), 24h (medium), 168h (low). Channels flagged is_likely_clipper
-- or with avg_duration_sec < 900 are set to REJECTED (row preserved for dedup).
CREATE TABLE IF NOT EXISTS discovery_channel (
    id TEXT PRIMARY KEY,
    youtube_channel_id TEXT NOT NULL,
    channel_name TEXT NOT NULL,
    channel_url TEXT NOT NULL,
    primary_category TEXT,
    avg_duration_sec INTEGER,
    median_view_count BIGINT,
    uploads_per_week REAL,
    subscriber_count BIGINT,
    is_likely_clipper_channel INTEGER DEFAULT 0,
    trust_score REAL DEFAULT 0.5,
    trust_samples INTEGER DEFAULT 0,
    poll_cadence_hours INTEGER DEFAULT 24,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    first_seen_at TEXT NOT NULL,
    last_crawled_at TEXT,
    last_profile_refresh_at TEXT,
    UNIQUE(youtube_channel_id)
);
CREATE INDEX IF NOT EXISTS idx_dc_status_poll ON discovery_channel(status, last_crawled_at);
CREATE INDEX IF NOT EXISTS idx_dc_trust ON discovery_channel(trust_score DESC);
CREATE INDEX IF NOT EXISTS idx_dc_category ON discovery_channel(primary_category);
