package com.viralclipper.repository;

import com.viralclipper.model.DiscoveredVideo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DiscoveredVideoRepository extends JpaRepository<DiscoveredVideo, String> {
    Optional<DiscoveredVideo> findByYoutubeId(String youtubeId);

    List<DiscoveredVideo> findByStatusOrderByPredictedScoreDescRelevanceScoreDesc(String status);

    @Query("SELECT d FROM DiscoveredVideo d WHERE d.status IN ('NEW','QUEUED') "
            + "ORDER BY COALESCE(d.predictedScore, d.relevanceScore) DESC, d.discoveredAt DESC")
    List<DiscoveredVideo> findActiveRanked();

    List<DiscoveredVideo> findByStatus(String status);
}
