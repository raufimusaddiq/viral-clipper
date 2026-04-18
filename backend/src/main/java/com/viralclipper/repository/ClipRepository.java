package com.viralclipper.repository;

import com.viralclipper.model.Clip;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ClipRepository extends JpaRepository<Clip, String> {
    List<Clip> findByVideoIdOrderByScoreDesc(String videoId);
    List<Clip> findByVideoIdAndTierInOrderByScoreDesc(String videoId, List<String> tiers);
}
