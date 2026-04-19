package com.viralclipper.repository;

import com.viralclipper.model.ClipFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClipFeedbackRepository extends JpaRepository<ClipFeedback, String> {

    Optional<ClipFeedback> findByClipId(String clipId);

    List<ClipFeedback> findByVideoId(String videoId);

    long countByActualViralScoreNotNull();

    List<ClipFeedback> findAllByActualViralScoreNotNull();
}
