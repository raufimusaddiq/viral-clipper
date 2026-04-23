package com.viralclipper.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A YouTube channel tracked by Discovery v2.
 *
 * <p>Channels surfaced by {@code runCategorySeed()} are persisted here and polled
 * on a trust-weighted cadence. {@code trust_score} is an EMA of
 * {@code clip_feedback.actual_viral_score} for clips imported from this channel —
 * high trust tightens the poll cadence (6h), low trust loosens it (168h).</p>
 *
 * <p>Rejection is not deletion: if avg duration is too short or the channel
 * looks like a clipper farm, we set {@code status=REJECTED} but keep the row
 * so the next category seed pass doesn't re-crawl it.</p>
 */
@Entity
@Table(name = "discovery_channel")
public class DiscoveryChannel {

    @Id
    private String id;

    @Column(name = "youtube_channel_id", nullable = false, unique = true)
    private String youtubeChannelId;

    @Column(name = "channel_name", nullable = false)
    private String channelName;

    @Column(name = "channel_url", nullable = false)
    private String channelUrl;

    @Column(name = "primary_category")
    private String primaryCategory;

    @Column(name = "avg_duration_sec")
    private Integer avgDurationSec;

    @Column(name = "median_view_count")
    private Long medianViewCount;

    @Column(name = "uploads_per_week")
    private Double uploadsPerWeek;

    @Column(name = "subscriber_count")
    private Long subscriberCount;

    @Column(name = "is_likely_clipper_channel")
    private Integer isLikelyClipperChannel;

    @Column(name = "trust_score")
    private Double trustScore;

    @Column(name = "trust_samples")
    private Integer trustSamples;

    @Column(name = "poll_cadence_hours")
    private Integer pollCadenceHours;

    @Column(nullable = false)
    private String status;

    @Column(name = "first_seen_at", nullable = false)
    private String firstSeenAt;

    @Column(name = "last_crawled_at")
    private String lastCrawledAt;

    @Column(name = "last_profile_refresh_at")
    private String lastProfileRefreshAt;

    public DiscoveryChannel() {}

    @PrePersist
    void prePersist() {
        if (firstSeenAt == null) firstSeenAt = Instant.now().toString();
        if (status == null) status = "ACTIVE";
        if (trustScore == null) trustScore = 0.5;
        if (trustSamples == null) trustSamples = 0;
        if (pollCadenceHours == null) pollCadenceHours = 24;
        if (isLikelyClipperChannel == null) isLikelyClipperChannel = 0;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getYoutubeChannelId() { return youtubeChannelId; }
    public void setYoutubeChannelId(String youtubeChannelId) { this.youtubeChannelId = youtubeChannelId; }
    public String getChannelName() { return channelName; }
    public void setChannelName(String channelName) { this.channelName = channelName; }
    public String getChannelUrl() { return channelUrl; }
    public void setChannelUrl(String channelUrl) { this.channelUrl = channelUrl; }
    public String getPrimaryCategory() { return primaryCategory; }
    public void setPrimaryCategory(String primaryCategory) { this.primaryCategory = primaryCategory; }
    public Integer getAvgDurationSec() { return avgDurationSec; }
    public void setAvgDurationSec(Integer avgDurationSec) { this.avgDurationSec = avgDurationSec; }
    public Long getMedianViewCount() { return medianViewCount; }
    public void setMedianViewCount(Long medianViewCount) { this.medianViewCount = medianViewCount; }
    public Double getUploadsPerWeek() { return uploadsPerWeek; }
    public void setUploadsPerWeek(Double uploadsPerWeek) { this.uploadsPerWeek = uploadsPerWeek; }
    public Long getSubscriberCount() { return subscriberCount; }
    public void setSubscriberCount(Long subscriberCount) { this.subscriberCount = subscriberCount; }
    public Integer getIsLikelyClipperChannel() { return isLikelyClipperChannel; }
    public void setIsLikelyClipperChannel(Integer isLikelyClipperChannel) { this.isLikelyClipperChannel = isLikelyClipperChannel; }
    public Double getTrustScore() { return trustScore; }
    public void setTrustScore(Double trustScore) { this.trustScore = trustScore; }
    public Integer getTrustSamples() { return trustSamples; }
    public void setTrustSamples(Integer trustSamples) { this.trustSamples = trustSamples; }
    public Integer getPollCadenceHours() { return pollCadenceHours; }
    public void setPollCadenceHours(Integer pollCadenceHours) { this.pollCadenceHours = pollCadenceHours; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(String firstSeenAt) { this.firstSeenAt = firstSeenAt; }
    public String getLastCrawledAt() { return lastCrawledAt; }
    public void setLastCrawledAt(String lastCrawledAt) { this.lastCrawledAt = lastCrawledAt; }
    public String getLastProfileRefreshAt() { return lastProfileRefreshAt; }
    public void setLastProfileRefreshAt(String lastProfileRefreshAt) { this.lastProfileRefreshAt = lastProfileRefreshAt; }
}
