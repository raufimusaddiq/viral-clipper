package com.viralclipper.model;

import jakarta.persistence.*;

@Entity
@Table(name = "clip")
public class Clip {

    @Id
    private String id;

    @Column(name = "video_id", nullable = false)
    private String videoId;

    @Column(name = "rank_pos")
    private Integer rankPos;

    private Double score;

    @Column(nullable = false)
    private String tier;

    @Column(name = "start_time", nullable = false)
    private Double startTime;

    @Column(name = "end_time", nullable = false)
    private Double endTime;

    @Column(name = "duration_sec", nullable = false)
    private Double durationSec;

    @Column(name = "text_content")
    private String textContent;

    @Column(name = "render_status", nullable = false)
    private String renderStatus = "PENDING";

    @Column(name = "render_path")
    private String renderPath;

    @Column(name = "export_status", nullable = false)
    private String exportStatus = "PENDING";

    @Column(name = "export_path")
    private String exportPath;

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    public Clip() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getVideoId() { return videoId; }
    public void setVideoId(String videoId) { this.videoId = videoId; }
    public Integer getRankPos() { return rankPos; }
    public void setRankPos(Integer rankPos) { this.rankPos = rankPos; }
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }
    public Double getStartTime() { return startTime; }
    public void setStartTime(Double startTime) { this.startTime = startTime; }
    public Double getEndTime() { return endTime; }
    public void setEndTime(Double endTime) { this.endTime = endTime; }
    public Double getDurationSec() { return durationSec; }
    public void setDurationSec(Double durationSec) { this.durationSec = durationSec; }
    public String getTextContent() { return textContent; }
    public void setTextContent(String textContent) { this.textContent = textContent; }
    public String getRenderStatus() { return renderStatus; }
    public void setRenderStatus(String renderStatus) { this.renderStatus = renderStatus; }
    public String getRenderPath() { return renderPath; }
    public void setRenderPath(String renderPath) { this.renderPath = renderPath; }
    public String getExportStatus() { return exportStatus; }
    public void setExportStatus(String exportStatus) { this.exportStatus = exportStatus; }
    public String getExportPath() { return exportPath; }
    public void setExportPath(String exportPath) { this.exportPath = exportPath; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
