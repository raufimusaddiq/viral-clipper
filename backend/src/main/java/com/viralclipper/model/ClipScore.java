package com.viralclipper.model;

import jakarta.persistence.*;

@Entity
@Table(name = "clip_score")
public class ClipScore {

    @Id
    private String id;

    @Column(name = "clip_id", nullable = false, unique = true)
    private String clipId;

    private Double hookStrength;
    private Double keywordTrigger;
    private Double novelty;
    private Double clarity;
    private Double emotionalEnergy;
    private Double textSentiment;
    private Double pauseStructure;
    private Double facePresence;
    private Double sceneChange;
    private Double topicFit;
    private Double historyScore;

    @Column(nullable = false)
    private Double boostTotal = 0.0;

    @Column(nullable = false)
    private Double penaltyTotal = 0.0;

    public ClipScore() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getClipId() { return clipId; }
    public void setClipId(String clipId) { this.clipId = clipId; }
    public Double getHookStrength() { return hookStrength; }
    public void setHookStrength(Double hookStrength) { this.hookStrength = hookStrength; }
    public Double getKeywordTrigger() { return keywordTrigger; }
    public void setKeywordTrigger(Double keywordTrigger) { this.keywordTrigger = keywordTrigger; }
    public Double getNovelty() { return novelty; }
    public void setNovelty(Double novelty) { this.novelty = novelty; }
    public Double getClarity() { return clarity; }
    public void setClarity(Double clarity) { this.clarity = clarity; }
    public Double getEmotionalEnergy() { return emotionalEnergy; }
    public void setEmotionalEnergy(Double emotionalEnergy) { this.emotionalEnergy = emotionalEnergy; }
    public Double getTextSentiment() { return textSentiment; }
    public void setTextSentiment(Double textSentiment) { this.textSentiment = textSentiment; }
    public Double getPauseStructure() { return pauseStructure; }
    public void setPauseStructure(Double pauseStructure) { this.pauseStructure = pauseStructure; }
    public Double getFacePresence() { return facePresence; }
    public void setFacePresence(Double facePresence) { this.facePresence = facePresence; }
    public Double getSceneChange() { return sceneChange; }
    public void setSceneChange(Double sceneChange) { this.sceneChange = sceneChange; }
    public Double getTopicFit() { return topicFit; }
    public void setTopicFit(Double topicFit) { this.topicFit = topicFit; }
    public Double getHistoryScore() { return historyScore; }
    public void setHistoryScore(Double historyScore) { this.historyScore = historyScore; }
    public Double getBoostTotal() { return boostTotal; }
    public void setBoostTotal(Double boostTotal) { this.boostTotal = boostTotal; }
    public Double getPenaltyTotal() { return penaltyTotal; }
    public void setPenaltyTotal(Double penaltyTotal) { this.penaltyTotal = penaltyTotal; }
}
