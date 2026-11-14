package com.civicair.briefing.ingestion;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QualityIssueRepository extends JpaRepository<QualityIssueEntity, UUID> {
    List<QualityIssueEntity> findAllByDatasetIdOrderByRowNumberAsc(UUID datasetId);
}
