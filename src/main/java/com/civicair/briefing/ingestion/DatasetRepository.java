package com.civicair.briefing.ingestion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatasetRepository extends JpaRepository<DatasetEntity, UUID> {
    List<DatasetEntity> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
    Optional<DatasetEntity> findByIdAndOwnerId(UUID id, UUID ownerId);
}
