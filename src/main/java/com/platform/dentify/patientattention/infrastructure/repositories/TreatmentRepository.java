package com.platform.dentify.patientattention.infrastructure.repositories;

import com.platform.dentify.patientattention.domain.model.aggregates.Treatment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TreatmentRepository extends JpaRepository<Treatment, Long> {
    List<Treatment> findAllByUser_IdOrderByNameAsc(Long userId);
    Optional<Treatment> findByIdAndUser_Id(Long id, Long userId);
    boolean existsByNameIgnoreCaseAndUser_Id(String name, Long userId);
    void deleteByIdAndUser_Id(Long id, Long userId);
}
