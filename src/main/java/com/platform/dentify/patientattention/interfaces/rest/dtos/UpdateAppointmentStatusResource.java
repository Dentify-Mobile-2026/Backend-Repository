package com.platform.dentify.patientattention.interfaces.rest.dtos;

public record UpdateAppointmentStatusResource(
        String status,
        String clinicalNotes,
        Boolean recordInMedicalHistory
) {
}
