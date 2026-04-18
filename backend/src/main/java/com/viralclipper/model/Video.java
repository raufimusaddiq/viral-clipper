package com.viralclipper.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "video")
public class Video {

    @Id
    private String id;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    private String title;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    public Video() {}

    public Video(String id, String sourceUrl, String sourceType, String filePath) {
        this.id = id;
        this.sourceUrl = sourceUrl;
        this.sourceType = sourceType;
        this.filePath = filePath;
        this.createdAt = Instant.now().toString();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getDurationSec() { return durationSec; }
    public void setDurationSec(Integer durationSec) { this.durationSec = durationSec; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
