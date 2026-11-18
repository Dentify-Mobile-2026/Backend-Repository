package com.platform.dentify.patientattention.domain.model.commands;

public record DeleteTreatmentCommand(Long id) {
    public DeleteTreatmentCommand {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Treatment ID must be positive");
        }
    }
}
