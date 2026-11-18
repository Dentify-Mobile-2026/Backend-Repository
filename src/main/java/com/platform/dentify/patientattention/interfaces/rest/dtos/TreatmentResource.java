package com.platform.dentify.patientattention.interfaces.rest.dtos;

public record TreatmentResource(
        Long id,
        String name,
        String description,
        String category,
        Double price,
        Integer estimatedDurationMinutes,
        boolean isActive
) {
}
