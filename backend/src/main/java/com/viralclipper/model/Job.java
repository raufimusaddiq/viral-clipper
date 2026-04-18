package com.viralclipper.model;

import jakarta.persistence.*;

@Entity
@Table(name = "job")
public class Job {

    @Id
    private String id;

    @Column(name = "video_id", nullable = false)
    private String videoId;

    @Column(nullable = false)
    private String status = "QUEUED";

    @Column(name = "current_stage")
    private String currentStage;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    @Column(name = "updated_at", nullable = false)
    private String updatedAt;

    public Job() {}

    public Job(String id, String videoId) {
        this.id = id;
        this.videoId = videoId;
        this.createdAt = java.time.Instant.now().toString();
        this.updatedAt = this.createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getVideoId() { return videoId; }
    public void setVideoId(String videoId) { this.videoId = videoId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; this.updatedAt = java.time.Instant.now().toString(); }
    public String getCurrentStage() { return currentStage; }
    public void setCurrentStage(String currentStage) { this.currentStage = currentStage; this.updatedAt = java.time.Instant.now().toString(); }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
