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

-- Migrations for existing databases
ALTER TABLE clip ADD COLUMN title TEXT;
ALTER TABLE clip ADD COLUMN description TEXT;
ALTER TABLE clip_score ADD COLUMN text_sentiment REAL;
