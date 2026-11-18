package com.platform.dentify.patientattention.domain.model.valueobjects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Size;

@Embeddable
public record PhoneNumber(
        @Size(max = 30)
        @Column(name = "phone", length = 30)
        String phone
) {
    public PhoneNumber {
        phone = (phone == null) ? "" : phone.trim();
    }

    public PhoneNumber() {
        this("");
    }
}
