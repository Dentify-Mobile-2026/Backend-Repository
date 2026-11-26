package com.platform.dentify.patientattention.application.internal;

import com.platform.dentify.iam.infrastructure.repositories.UserRepository;
import com.platform.dentify.iam.infrastructure.security.AuthenticatedUserProvider;
import com.platform.dentify.patientattention.domain.exceptions.AppointmentOutsideBusinessHoursException;
import com.platform.dentify.patientattention.domain.model.aggregates.Appointment;
import com.platform.dentify.patientattention.domain.model.commands.UpdateBusinessHoursCommand;
import com.platform.dentify.patientattention.domain.model.entities.BusinessDay;
import com.platform.dentify.patientattention.domain.services.BusinessHoursService;
import com.platform.dentify.patientattention.infrastructure.repositories.BusinessDayRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@Service
public class BusinessHoursServiceImpl implements BusinessHoursService {

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    private final BusinessDayRepository businessDayRepository;
    private final UserRepository userRepository;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    public BusinessHoursServiceImpl(BusinessDayRepository businessDayRepository,
                                    UserRepository userRepository,
                                    AuthenticatedUserProvider authenticatedUserProvider) {
        this.businessDayRepository = businessDayRepository;
        this.userRepository = userRepository;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    @Override
    public List<BusinessDay> getForCurrentUser() {
        return sorted(businessDayRepository.findAllByUser_Id(authenticatedUserProvider.getCurrentUserId()));
    }

    @Override
    @Transactional
    public List<BusinessDay> handle(UpdateBusinessHoursCommand command) {
        Long userId = authenticatedUserProvider.getCurrentUserId();
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        // Se actualiza cada día en su fila: borrar e insertar choca con la restricción única,
        // porque Hibernate ejecuta las inserciones antes que los borrados.
        for (var day : command.days()) {
            var row = businessDayRepository.findByUser_IdAndDayOfWeek(userId, day.dayOfWeek())
                    .map(existing -> {
                        existing.update(day.open(), day.opensAt(), day.closesAt());
                        return existing;
                    })
                    .orElseGet(() -> new BusinessDay(user, day.dayOfWeek(), day.open(), day.opensAt(), day.closesAt()));
            businessDayRepository.save(row);
        }
        return sorted(businessDayRepository.findAllByUser_Id(userId));
    }

    @Override
    public void assertWithinBusinessHours(Long userId, LocalDateTime start, LocalTime duration) {
        var days = businessDayRepository.findAllByUser_Id(userId);
        if (days.isEmpty()) {
            return; // El consultorio no definió su horario: no se restringe nada.
        }
        LocalDateTime end = start.plus(Appointment.toDuration(duration));
        var day = days.stream().filter(d -> d.getDayOfWeek() == start.getDayOfWeek()).findFirst();

        if (day.isEmpty() || !day.get().isOpen()) {
            throw new AppointmentOutsideBusinessHoursException("El consultorio no atiende ese día.");
        }
        if (!day.get().admits(start, end)) {
            throw new AppointmentOutsideBusinessHoursException(
                    "La cita está fuera del horario de atención (" + day.get().getOpensAt().format(HOUR)
                            + " a " + day.get().getClosesAt().format(HOUR) + ").");
        }
    }

    private static List<BusinessDay> sorted(List<BusinessDay> days) {
        return days.stream().sorted(Comparator.comparing(BusinessDay::getDayOfWeek)).toList();
    }
}
