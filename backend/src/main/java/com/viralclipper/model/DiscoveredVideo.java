package com.viralclipper.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "discovered_video")
public class DiscoveredVideo {

    @Id
    private String id;

    @Column(name = "youtube_id", nullable = false, unique = true)
    private String youtubeId;

    @Column(nullable = false)
    private String url;

    private String title;
    private String channel;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @Column(name = "view_count")
    private Long viewCount;

    @Column(name = "upload_date")
    private String uploadDate;

    @Column(name = "age_hours")
    private Double ageHours;

    @Column(name = "source_mode", nullable = false)
    private String sourceMode;

    @Column(name = "source_query")
    private String sourceQuery;

    @Column(name = "relevance_score")
    private Double relevanceScore;

    @Column(name = "transcript_score")
    private Double transcriptScore;

    @Column(name = "predicted_score")
    private Double predictedScore;

    @Column(name = "transcript_sample")
    private String transcriptSample;

    @Column(nullable = false)
    private String status;

    @Column(name = "job_id")
    private String jobId;

    @Column(name = "video_id")
    private String videoId;

    @Column(name = "discovered_at", nullable = false)
    private String discoveredAt;

    @Column(name = "enriched_at")
    private String enrichedAt;

    public DiscoveredVideo() {}

    @PrePersist
    void prePersist() {
        if (discoveredAt == null) discoveredAt = Instant.now().toString();
        if (status == null) status = "NEW";
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getYoutubeId() { return youtubeId; }
    public void setYoutubeId(String youtubeId) { this.youtubeId = youtubeId; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public Integer getDurationSec() { return durationSec; }
    public void setDurationSec(Integer durationSec) { this.durationSec = durationSec; }
    public Long getViewCount() { return viewCount; }
    public void setViewCount(Long viewCount) { this.viewCount = viewCount; }
    public String getUploadDate() { return uploadDate; }
    public void setUploadDate(String uploadDate) { this.uploadDate = uploadDate; }
    public Double getAgeHours() { return ageHours; }
    public void setAgeHours(Double ageHours) { this.ageHours = ageHours; }
    public String getSourceMode() { return sourceMode; }
    public void setSourceMode(String sourceMode) { this.sourceMode = sourceMode; }
    public String getSourceQuery() { return sourceQuery; }
    public void setSourceQuery(String sourceQuery) { this.sourceQuery = sourceQuery; }
    public Double getRelevanceScore() { return relevanceScore; }
    public void setRelevanceScore(Double relevanceScore) { this.relevanceScore = relevanceScore; }
    public Double getTranscriptScore() { return transcriptScore; }
    public void setTranscriptScore(Double transcriptScore) { this.transcriptScore = transcriptScore; }
    public Double getPredictedScore() { return predictedScore; }
    public void setPredictedScore(Double predictedScore) { this.predictedScore = predictedScore; }
    public String getTranscriptSample() { return transcriptSample; }
    public void setTranscriptSample(String transcriptSample) { this.transcriptSample = transcriptSample; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public String getVideoId() { return videoId; }
    public void setVideoId(String videoId) { this.videoId = videoId; }
    public String getDiscoveredAt() { return discoveredAt; }
    public void setDiscoveredAt(String discoveredAt) { this.discoveredAt = discoveredAt; }
    public String getEnrichedAt() { return enrichedAt; }
    public void setEnrichedAt(String enrichedAt) { this.enrichedAt = enrichedAt; }
}
