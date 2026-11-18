package com.platform.dentify.patientattention.domain.model.valueobjects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Size;

@Embeddable
public record EmergencyContact(
        @Size(max = 120)
        @Column(name = "emergency_contact_name", length = 120)
        String emergencyContactName,

        @Size(max = 30)
        @Column(name = "emergency_contact_phone", length = 30)
        String emergencyContactPhone
) {
    public EmergencyContact {
        emergencyContactName = (emergencyContactName == null) ? "" : emergencyContactName.trim();
        emergencyContactPhone = (emergencyContactPhone == null) ? "" : emergencyContactPhone.trim();
    }

    public EmergencyContact() {
        this("", "");
    }
}
