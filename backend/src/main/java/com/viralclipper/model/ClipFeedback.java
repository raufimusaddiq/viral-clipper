package com.viralclipper.model;

import jakarta.persistence.*;

@Entity
@Table(name = "clip_feedback")
public class ClipFeedback {

    @Id
    private String id;

    @Column(name = "clip_id", nullable = false)
    private String clipId;

    @Column(name = "video_id", nullable = false)
    private String videoId;

    @Column(nullable = false)
    private String features;

    @Column(name = "predicted_score", nullable = false)
    private Double predictedScore;

    @Column(name = "predicted_tier", nullable = false)
    private String predictedTier;

    @Column(name = "tiktok_views")
    private Integer tiktokViews = 0;

    @Column(name = "tiktok_likes")
    private Integer tiktokLikes = 0;

    @Column(name = "tiktok_comments")
    private Integer tiktokComments = 0;

    @Column(name = "tiktok_shares")
    private Integer tiktokShares = 0;

    @Column(name = "tiktok_saves")
    private Integer tiktokSaves = 0;

    @Column(name = "actual_viral_score")
    private Double actualViralScore;

    @Column(name = "posted_at")
    private String postedAt;

    @Column(name = "last_checked")
    private String lastChecked;

    @Column(name = "created_at")
    private String createdAt;

    public ClipFeedback() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getClipId() { return clipId; }
    public void setClipId(String clipId) { this.clipId = clipId; }
    public String getVideoId() { return videoId; }
    public void setVideoId(String videoId) { this.videoId = videoId; }
    public String getFeatures() { return features; }
    public void setFeatures(String features) { this.features = features; }
    public Double getPredictedScore() { return predictedScore; }
    public void setPredictedScore(Double predictedScore) { this.predictedScore = predictedScore; }
    public String getPredictedTier() { return predictedTier; }
    public void setPredictedTier(String predictedTier) { this.predictedTier = predictedTier; }
    public Integer getTiktokViews() { return tiktokViews; }
    public void setTiktokViews(Integer tiktokViews) { this.tiktokViews = tiktokViews; }
    public Integer getTiktokLikes() { return tiktokLikes; }
    public void setTiktokLikes(Integer tiktokLikes) { this.tiktokLikes = tiktokLikes; }
    public Integer getTiktokComments() { return tiktokComments; }
    public void setTiktokComments(Integer tiktokComments) { this.tiktokComments = tiktokComments; }
    public Integer getTiktokShares() { return tiktokShares; }
    public void setTiktokShares(Integer tiktokShares) { this.tiktokShares = tiktokShares; }
    public Integer getTiktokSaves() { return tiktokSaves; }
    public void setTiktokSaves(Integer tiktokSaves) { this.tiktokSaves = tiktokSaves; }
    public Double getActualViralScore() { return actualViralScore; }
    public void setActualViralScore(Double actualViralScore) { this.actualViralScore = actualViralScore; }
    public String getPostedAt() { return postedAt; }
    public void setPostedAt(String postedAt) { this.postedAt = postedAt; }
    public String getLastChecked() { return lastChecked; }
    public void setLastChecked(String lastChecked) { this.lastChecked = lastChecked; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
