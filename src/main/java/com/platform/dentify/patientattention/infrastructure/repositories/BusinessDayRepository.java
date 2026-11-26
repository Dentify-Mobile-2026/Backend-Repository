package com.platform.dentify.patientattention.infrastructure.repositories;

import com.platform.dentify.patientattention.domain.model.entities.BusinessDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

@Repository
public interface BusinessDayRepository extends JpaRepository<BusinessDay, Long> {
    List<BusinessDay> findAllByUser_Id(Long userId);

    Optional<BusinessDay> findByUser_IdAndDayOfWeek(Long userId, DayOfWeek dayOfWeek);
}
