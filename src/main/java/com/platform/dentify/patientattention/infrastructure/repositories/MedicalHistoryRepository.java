package com.platform.dentify.patientattention.infrastructure.repositories;

import com.platform.dentify.patientattention.domain.model.entities.MedicalHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MedicalHistoryRepository extends JpaRepository<MedicalHistory, Long> {
    boolean existsBySourceAppointmentId(Long appointmentId);
    List<MedicalHistory> findAllByPatientId(Long patientId);

    Optional<MedicalHistory> findByIdAndPatient_IdAndPatient_User_Id(Long id, Long patientId, Long userId);

}
