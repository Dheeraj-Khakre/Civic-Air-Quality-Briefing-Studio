package com.civicair.briefing.briefing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BriefRepository extends JpaRepository<BriefEntity, UUID> {
    List<BriefEntity> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
    Optional<BriefEntity> findByIdAndOwnerId(UUID id, UUID ownerId);
}
