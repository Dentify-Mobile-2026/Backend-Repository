package com.platform.dentify.patientattention.infrastructure.repositories;

import com.platform.dentify.patientattention.domain.model.aggregates.TreatmentPlan;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TreatmentPlanRepository extends JpaRepository<TreatmentPlan, Long> {
    @EntityGraph(attributePaths = {"items", "items.treatment"})
    List<TreatmentPlan> findAllByPatient_IdAndUser_IdOrderByCreatedAtDesc(Long patientId, Long userId);

    @EntityGraph(attributePaths = {"items", "items.treatment"})
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<TreatmentPlan> findByIdAndUser_Id(Long id, Long userId);
}
