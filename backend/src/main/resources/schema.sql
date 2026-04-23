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
