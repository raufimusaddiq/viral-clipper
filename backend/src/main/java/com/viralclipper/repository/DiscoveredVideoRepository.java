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

    /**
     * Active candidates no older than {@code maxAgeHours}.
     *
     * <p>Unknown-age rows (age_hours >= 9999 or null) are REJECTED — these come
     * from {@code --flat-playlist} paths where yt-dlp strips upload_date, and
     * a 6-year-old video with stripped date would otherwise pose as "unknown"
     * and leak into the default Discover view. Safer to hide than to show.
     * Users wanting those can still list a specific status filter
     * (IMPORTED/SKIPPED) which bypasses this query.</p>
     */
    @Query("SELECT d FROM DiscoveredVideo d WHERE d.status IN ('NEW','QUEUED') "
            + "AND d.ageHours IS NOT NULL AND d.ageHours < 9999 AND d.ageHours <= :maxAgeHours "
            + "ORDER BY COALESCE(d.predictedScore, d.relevanceScore) DESC, d.discoveredAt DESC")
    List<DiscoveredVideo> findActiveRankedRecent(double maxAgeHours);

    List<DiscoveredVideo> findByStatus(String status);
}
