package com.platform.dentify.patientattention.domain.model.commands;

public record UpdateAppointmentStatusCommand(Long id, String status, String clinicalNotes, boolean recordInMedicalHistory) {
    public UpdateAppointmentStatusCommand(Long id, String status, String clinicalNotes) {
        this(id, status, clinicalNotes, false);
    }

    public UpdateAppointmentStatusCommand {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Appointment ID must be positive");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Appointment status cannot be blank");
        }
        if (clinicalNotes != null && clinicalNotes.length() > 500) {
            throw new IllegalArgumentException("Clinical notes support at most 500 characters");
        }
    }
}
