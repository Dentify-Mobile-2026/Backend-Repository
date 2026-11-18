package com.platform.dentify.patientattention.interfaces.rest.dtos;

import java.time.LocalDateTime;
import java.time.LocalTime;

public record AppointmentResource(
        Long id,
        String patientName,
        String dni,
        String patientPhone,
        LocalDateTime appointmentDate,
        String reason,
        Boolean completed,
        String status,
        String clinicalNotes,
        Long treatmentId,
        String treatmentName,
        LocalTime duration,
        LocalDateTime createdAt
) {
}
