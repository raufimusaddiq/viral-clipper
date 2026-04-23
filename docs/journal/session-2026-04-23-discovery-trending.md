---
description: 'Trending fix: hashtag feeds instead of keyword search, real upload dates, Shorts excluded'
label: session-2026-04-23-discovery-trending
---

## Bug reported
Top trending video was 6 years old. Trending was keyword-hunting, not trending.

## Root causes

1. **`--flat-playlist` strips `upload_date`.** Every candidate got `age_hours=999`, so the velocity filter in `quick_relevance_score` was a no-op. A 6-year-old video with "viral" in the title scored higher than a 1-day-old one because only title keywords mattered.
2. **"Trending" wasn't trending.** `discover_trending` ran 6 hardcoded keyword searches. That's `search`, not trending — YouTube's own ranking wasn't consulted.
3. **YouTube Shorts polluted results.** Shorts are already clipped content; no source material to extract from.

## Fixes

**Real trending source** (`ai-pipeline/discover.py`):
- Replaced keyword-search trending with **YouTube hashtag feeds** (`/hashtag/viralindonesia`, `/hashtag/viral`, `/hashtag/beritaviral`, `/hashtag/terbaru`, `/hashtag/fyp`). YouTube deprecated `/feed/trending` in late 2024; hashtag feeds are the closest accessible signal.
- Niche search supplement (`trending_niche_id`: streamer/podcast/influencer queries) kept as a secondary source because hashtag feeds skew toward news/drama.
- `discover_hashtag(hashtag, ...)` is the new primitive.

**Real upload dates**:
- New `recent_only=True` / `max_age_days` params on `discover_search`. When set, drop `--flat-playlist` (slower but `upload_date` is populated) and add `--match-filters "upload_date >=(today-Ndays)"` for server-side filtering.
- `ytsearchdate` prefix is NOT supported in yt-dlp 2026.03.17 — verified and documented. We rely on YouTube's own recency bias + match-filters.
- Belt-and-suspenders Python-side age filter for entries that slip through the yt-dlp filter (common on playlist feeds with `--skip-download`).

**Shorts exclusion** (`is_short`):
- New helper checks **URL pattern** (`/shorts/` fragment) AND **duration ≤ 60s**. Either signal is enough.
- Added `exclude_shorts=True` default to `discover_search`, `discover_hashtag`, `discover_channel`.
- Missing/zero duration is explicitly NOT flagged as Short — only positive evidence counts.

**Parallel hashtag fetches**:
- 5 hashtags × ~12 non-flat fetches each = ~4 minutes sequential. `ThreadPoolExecutor(max_workers=5)` brings it to **~69s** for 10 real trending videos.

## Verification

Live trending call returns 10 real viral Indonesian videos, all age 19h–42d, all 121–258s (legitimate source material, no Shorts), all with real view counts. Sample:
```
age=139h dur=222s views=285369 — "Viral Aksi Seorang Wanita yang Teriak Histeris…"
age=619h dur=151s views=971571 — "Selingkuhi Istri Orang Oknum Ustadz Babak Belur…"
age=19h dur=170s views=114255 — "KACAU! Refly Harun Hampir Baku Hantam…"
```

pytest: 209/209 pass (added `TestShortsDetection` — 4 tests for URL pattern + duration thresholds).

## Deferred

- The 69s fetch is still slow enough that the UI needs a loading state. Current frontend shows "Searching…" but doesn't stream partial results as hashtags complete.
- Could further speed up by using `--flat-playlist` for the initial hashtag fetch and deferring age/upload_date to the enrichment phase — but flat mode strips duration, which would defeat the Shorts filter.
