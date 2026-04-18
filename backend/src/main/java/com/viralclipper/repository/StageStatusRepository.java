package com.viralclipper.repository;

import com.viralclipper.model.StageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StageStatusRepository extends JpaRepository<StageStatus, String> {
    List<StageStatus> findByJobId(String jobId);
}
