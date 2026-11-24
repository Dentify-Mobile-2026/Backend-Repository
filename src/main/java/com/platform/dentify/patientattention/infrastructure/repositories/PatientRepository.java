package com.platform.dentify.patientattention.infrastructure.repositories;

import com.platform.dentify.patientattention.domain.model.aggregates.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {
    List<Patient> findAllByUser_Id(Long userId);
    Optional<Patient> findByIdAndUser_Id(Long patientId, Long userId);
    //Name_FirstName → esto le indica a Spring Data JPA que navegue dentro del embebido name
    boolean existsByName_FirstNameAndName_LastNameAndUser_Id(String firstName, String lastName, Long userId);
    boolean existsByDni_DniAndUser_Id(String dni, Long userId);
    boolean existsByDni_DniAndUser_IdAndIdNot(String dni, Long userId, Long id);
    void deleteByIdAndUser_Id(Long id, Long userId);
}
