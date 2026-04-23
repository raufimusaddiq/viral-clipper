package com.viralclipper.repository;

import com.viralclipper.model.DiscoveryChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DiscoveryChannelRepository extends JpaRepository<DiscoveryChannel, String> {

    Optional<DiscoveryChannel> findByYoutubeChannelId(String youtubeChannelId);

    List<DiscoveryChannel> findByStatus(String status);

    /**
     * Channels whose poll window has elapsed. Includes rows with
     * {@code last_crawled_at IS NULL} (never crawled). Scheduler polls these.
     */
    @Query("SELECT c FROM DiscoveryChannel c WHERE c.status = 'ACTIVE' "
            + "AND (c.lastCrawledAt IS NULL OR c.lastCrawledAt < :cutoff) "
            + "ORDER BY COALESCE(c.trustScore, 0.5) DESC")
    List<DiscoveryChannel> findActiveDueForCrawl(String cutoff);

    @Query("SELECT c FROM DiscoveryChannel c WHERE c.status = 'ACTIVE' "
            + "ORDER BY c.trustScore DESC, c.medianViewCount DESC")
    List<DiscoveryChannel> findActiveByTrust();
}
