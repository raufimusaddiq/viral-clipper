package com.viralclipper.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.viralclipper.model.DiscoveryChannel;
import com.viralclipper.pipeline.PythonRunner;
import com.viralclipper.repository.DiscoveryChannelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Phase 2 channel orchestration.
 *
 * <p>{@link #runCategorySeed()} asks Python for the 12-query anchor crawl, then
 * upserts each surfaced channel. New rows are profiled inline on a bounded
 * pool so a seed run fans out to ~6 concurrent {@code yt-dlp} profile fetches
 * without saturating the network stack.</p>
 *
 * <p>Rejection policy: profile sets {@code status=REJECTED} when the channel's
 * average duration is below 15 min or the clipper heuristic fires. The row is
 * preserved so the next seed pass skips it — cheaper than re-crawling and
 * cheaper than relying on in-memory dedup.</p>
 */
@Service
public class ChannelCrawlerService {

    private static final Logger log = LoggerFactory.getLogger(ChannelCrawlerService.class);

    /** Channels with avg upload below this fail the long-form source gate. */
    private static final int MIN_AVG_DURATION_SEC = 15 * 60;

    /** Cap on parallel profile calls. Each hits yt-dlp with a non-flat pull. */
    private static final int PROFILE_CONCURRENCY = 6;

    private final PythonRunner pythonRunner;
    private final DiscoveryChannelRepository channelRepo;
    private final ExecutorService profilePool;

    public ChannelCrawlerService(PythonRunner pythonRunner,
                                 DiscoveryChannelRepository channelRepo) {
        this.pythonRunner = pythonRunner;
        this.channelRepo = channelRepo;

        AtomicLong tid = new AtomicLong();
        this.profilePool = new ThreadPoolExecutor(
                PROFILE_CONCURRENCY, PROFILE_CONCURRENCY,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(64),
                r -> {
                    Thread t = new Thread(r, "chan-profile-" + tid.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @PreDestroy
    public void shutdown() {
        profilePool.shutdown();
        try {
            if (!profilePool.awaitTermination(10, TimeUnit.SECONDS)) {
                profilePool.shutdownNow();
            }
        } catch (InterruptedException e) {
            profilePool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // -- seed ----------------------------------------------------------------

    /**
     * Run the category-seed crawl and upsert surfaced channels. Profile work
     * is queued fire-and-forget on the profile pool so the HTTP caller is not
     * held for minutes while yt-dlp crawls ~100 channels.
     *
     * <p>Return value reports what was enqueued; use
     * {@code GET /api/discover/channels} to watch profiles land. A newly-seeded
     * channel shows {@code avgDurationSec=null, lastProfileRefreshAt=null} until
     * its profile completes; then the row transitions to ACTIVE-with-stats or
     * REJECTED.</p>
     */
    public SeedResult runCategorySeed() throws Exception {
        log.info("Starting category-seed crawl");
        JsonNode data = pythonRunner.runScript("channel_crawler.py",
                List.of("--mode", "category_seed"));

        List<DiscoveryChannel> toProfile = new ArrayList<>();
        int alreadyKnown = 0;
        int upserted = 0;

        if (data.has("channels")) {
            for (JsonNode c : data.get("channels")) {
                String ytId = c.path("youtubeChannelId").asText("");
                if (ytId.isBlank()) continue;

                var existing = channelRepo.findByYoutubeChannelId(ytId);
                if (existing.isPresent()) {
                    alreadyKnown++;
                    // Re-enqueue unprofiled channels (previous seed may have
                    // been interrupted before profile work finished).
                    DiscoveryChannel prior = existing.get();
                    if (prior.getLastProfileRefreshAt() == null
                            && "ACTIVE".equals(prior.getStatus())) {
                        toProfile.add(prior);
                    }
                    continue;
                }

                DiscoveryChannel ch = new DiscoveryChannel();
                ch.setId(UUID.randomUUID().toString());
                ch.setYoutubeChannelId(ytId);
                ch.setChannelName(c.path("channelName").asText(""));
                ch.setChannelUrl(c.path("channelUrl").asText(""));
                ch.setFirstSeenAt(Instant.now().toString());
                ch.setStatus("ACTIVE");
                ch.setTrustScore(0.5);
                ch.setTrustSamples(0);
                ch.setPollCadenceHours(24);
                ch.setIsLikelyClipperChannel(0);
                synchronized (DB_WRITE_LOCK) {
                    toProfile.add(channelRepo.save(ch));
                }
                upserted++;
            }
        }

        // Fire-and-forget: enqueue profile work and return. The pool drains
        // async; individual failures are logged but don't fail the seed call.
        for (DiscoveryChannel ch : toProfile) {
            profilePool.submit(() -> {
                try {
                    profileChannelInternal(ch.getId());
                } catch (Exception e) {
                    log.warn("Profile failed for {}: {}", ch.getChannelUrl(), e.getMessage());
                }
            });
        }

        log.info("Category seed done: upserted={} alreadyKnown={} queued-for-profile={}",
                upserted, alreadyKnown, toProfile.size());
        return new SeedResult(upserted, toProfile.size(), 0, alreadyKnown);
    }

    // -- profile -------------------------------------------------------------

    /**
     * Re-profile an existing channel (manual refresh endpoint).
     * @return true if the channel was rejected by the long-form gate.
     */
    public boolean refreshProfile(String channelId) throws Exception {
        return profileChannelInternal(channelId);
    }

    /**
     * Shared profile path used by both seed fan-out and manual refresh.
     * @return true if the profile caused a rejection.
     */
    private boolean profileChannelInternal(String channelId) throws Exception {
        DiscoveryChannel ch = channelRepo.findById(channelId).orElse(null);
        if (ch == null) return false;

        JsonNode p = pythonRunner.runScript("channel_crawler.py",
                List.of("--mode", "profile", "--channel-url", ch.getChannelUrl()));

        int avgDur = p.path("avgDurationSec").asInt(0);
        int isClipper = p.path("isLikelyClipperChannel").asInt(0);

        synchronized (DB_WRITE_LOCK) {
            DiscoveryChannel fresh = channelRepo.findById(channelId).orElse(null);
            if (fresh == null) return false;

            // The profile output sometimes carries a corrected channel_name /
            // channel_id if yt-dlp disambiguated the URL; adopt it if non-empty.
            String name = p.path("channelName").asText("");
            if (!name.isBlank()) fresh.setChannelName(name);
            String ytId = p.path("channelId").asText("");
            if (!ytId.isBlank() && fresh.getYoutubeChannelId().isBlank()) {
                fresh.setYoutubeChannelId(ytId);
            }

            fresh.setAvgDurationSec(avgDur);
            fresh.setMedianViewCount(p.path("medianViewCount").asLong(0));
            fresh.setUploadsPerWeek(p.path("uploadsPerWeek").asDouble(0.0));
            fresh.setSubscriberCount(p.path("subscriberCount").asLong(0));
            fresh.setIsLikelyClipperChannel(isClipper);
            fresh.setPrimaryCategory(p.path("primaryCategory").asText("MIXED"));
            fresh.setLastProfileRefreshAt(Instant.now().toString());

            boolean rejected = false;
            if (avgDur > 0 && avgDur < MIN_AVG_DURATION_SEC) {
                fresh.setStatus("REJECTED");
                rejected = true;
            } else if (isClipper == 1) {
                fresh.setStatus("REJECTED");
                rejected = true;
            }
            channelRepo.save(fresh);
            return rejected;
        }
    }

    // -- admin ---------------------------------------------------------------

    public List<DiscoveryChannel> listActive() {
        return channelRepo.findActiveByTrust();
    }

    public List<DiscoveryChannel> listByStatus(String status) {
        return channelRepo.findByStatus(status);
    }

    public java.util.Optional<DiscoveryChannel> setStatus(String channelId, String status) {
        return channelRepo.findById(channelId).map(ch -> {
            ch.setStatus(status.toUpperCase());
            return channelRepo.save(ch);
        });
    }

    // Serialize writes to discovery_channel from seed + profile workers.
    // SQLite allows a single writer; without this lock parallel saves race.
    private static final Object DB_WRITE_LOCK = new Object();

    public record SeedResult(int upserted, int profiled, int rejected, int alreadyKnown) {}
}
