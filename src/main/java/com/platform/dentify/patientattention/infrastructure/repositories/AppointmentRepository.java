package com.platform.dentify.patientattention.infrastructure.repositories;

import com.platform.dentify.patientattention.domain.model.aggregates.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from Appointment a where a.id = :id and a.patient.user.id = :userId")
    Optional<Appointment> findForStatusUpdate(@org.springframework.data.repository.query.Param("id") Long id,
                                             @org.springframework.data.repository.query.Param("userId") Long userId);
    List<Appointment> findAllByPatientId(Long patientId);
    Optional<Appointment> findByIdAndPatient_User_Id(Long appointmentId, Long userId);
    boolean existsByIdAndPatient_User_Id(Long appointmentId, Long userId);
    List<Appointment> findAllByPatient_User_IdOrderByAppointmentDateDesc(Long userId);
    List<Appointment> findAllByPatient_User_IdAndAppointmentDateBetweenOrderByAppointmentDateAsc(Long userId, LocalDateTime start, LocalDateTime end);

}
