package com.platform.dentify.patientattention.interfaces.rest.dtos;

public record UpdateTreatmentResource(
        String name,
        String description,
        String category,
        Double price,
        Integer estimatedDurationMinutes,
        Boolean isActive
) {
}
