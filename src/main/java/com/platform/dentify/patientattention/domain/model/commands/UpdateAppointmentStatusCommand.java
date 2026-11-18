package com.platform.dentify.patientattention.domain.model.commands;

public record UpdateAppointmentStatusCommand(Long id, String status, String clinicalNotes) {
    public UpdateAppointmentStatusCommand {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Appointment ID must be positive");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Appointment status cannot be blank");
        }
    }
}
