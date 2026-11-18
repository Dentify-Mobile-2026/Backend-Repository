package com.platform.dentify.patientattention.domain.model.commands;

public record UpdateTreatmentCommand(
        Long id,
        String name,
        String description,
        String category,
        Double price,
        Integer estimatedDurationMinutes,
        Boolean isActive
) {
    public UpdateTreatmentCommand {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Treatment ID must be positive");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Treatment name cannot be blank");
        }
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Treatment category cannot be blank");
        }
        if (price == null || price < 0) {
            throw new IllegalArgumentException("Treatment price must be zero or positive");
        }
        if (estimatedDurationMinutes == null || estimatedDurationMinutes <= 0) {
            throw new IllegalArgumentException("Estimated duration must be greater than zero");
        }
        description = (description == null) ? "" : description.trim();
        isActive = (isActive == null) ? Boolean.TRUE : isActive;
    }
}
