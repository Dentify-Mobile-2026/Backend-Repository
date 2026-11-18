package com.platform.dentify.patientattention.domain.model.commands;

import java.time.LocalDate;

public record CreatePatientCommand(
        String dni,
        String firstName,
        String lastName,
        String email,
        LocalDate birthday,
        String homeAddress,
        String phone,
        String allergies,
        String systemicConditions,
        String emergencyContactName,
        String emergencyContactPhone
) {
}
