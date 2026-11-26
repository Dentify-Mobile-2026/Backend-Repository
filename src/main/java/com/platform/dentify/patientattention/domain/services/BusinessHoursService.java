package com.platform.dentify.patientattention.domain.services;

import com.platform.dentify.patientattention.domain.model.commands.UpdateBusinessHoursCommand;
import com.platform.dentify.patientattention.domain.model.entities.BusinessDay;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** Horario de atención del consultorio (US09). */
public interface BusinessHoursService {

    /** Días configurados por el consultorio autenticado; vacío si nunca definió su horario. */
    List<BusinessDay> getForCurrentUser();

    /**
     * Guarda el horario del consultorio autenticado; solo afecta a las citas que se creen o
     * reprogramen después. Una vez guardado, un día que nunca se envió cuenta como cerrado.
     */
    List<BusinessDay> handle(UpdateBusinessHoursCommand command);

    /**
     * Rechaza una cita fuera del horario. Si el consultorio no definió su horario, no restringe nada.
     *
     * @throws com.platform.dentify.patientattention.domain.exceptions.AppointmentOutsideBusinessHoursException
     */
    void assertWithinBusinessHours(Long userId, LocalDateTime start, LocalTime duration);
}
