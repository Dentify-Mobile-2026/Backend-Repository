package com.platform.dentify.patientattention.domain.model.valueobjects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Size;

@Embeddable
public record MedicalAlerts(
        @Size(max = 500)
        @Column(name = "allergies", length = 500)
        String allergies,

        @Size(max = 500)
        @Column(name = "systemic_conditions", length = 500)
        String systemicConditions
) {
    public MedicalAlerts {
        allergies = (allergies == null) ? "" : allergies.trim();
        systemicConditions = (systemicConditions == null) ? "" : systemicConditions.trim();
    }

    public MedicalAlerts() {
        this("", "");
    }
}
