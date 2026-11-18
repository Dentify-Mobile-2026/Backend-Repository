package com.platform.dentify.patientattention.interfaces.rest.dtos;

public record CreateTreatmentResource(
        String name,
        String description,
        String category,
        Double price,
        Integer estimatedDurationMinutes
) {
}
