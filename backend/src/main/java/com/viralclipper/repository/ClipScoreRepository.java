package com.viralclipper.repository;

import com.viralclipper.model.ClipScore;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClipScoreRepository extends JpaRepository<ClipScore, String> {
    ClipScore findByClipId(String clipId);
}
