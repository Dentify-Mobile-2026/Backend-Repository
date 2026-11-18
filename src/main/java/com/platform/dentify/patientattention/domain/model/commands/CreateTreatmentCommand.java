package com.platform.dentify.patientattention.domain.model.commands;

public record CreateTreatmentCommand(
        String name,
        String description,
        String category,
        Double price,
        Integer estimatedDurationMinutes
) {
    public CreateTreatmentCommand {
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
    }
}
