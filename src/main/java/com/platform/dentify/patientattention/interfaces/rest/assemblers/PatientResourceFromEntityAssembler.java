package com.platform.dentify.patientattention.interfaces.rest.assemblers;

import com.platform.dentify.patientattention.domain.model.aggregates.Patient;
import com.platform.dentify.patientattention.interfaces.rest.dtos.PatientResource;

public class PatientResourceFromEntityAssembler {

    public static PatientResource toResourceFromEntity(Patient entity) {
        String phone = (entity.getPhone() != null && entity.getPhone().phone() != null)
                ? entity.getPhone().phone() : "";
        String allergies = (entity.getMedicalAlerts() != null && entity.getMedicalAlerts().allergies() != null)
                ? entity.getMedicalAlerts().allergies() : "";
        String systemicConditions = (entity.getMedicalAlerts() != null && entity.getMedicalAlerts().systemicConditions() != null)
                ? entity.getMedicalAlerts().systemicConditions() : "";
        String emergencyContactName = (entity.getEmergencyContact() != null && entity.getEmergencyContact().emergencyContactName() != null)
                ? entity.getEmergencyContact().emergencyContactName() : "";
        String emergencyContactPhone = (entity.getEmergencyContact() != null && entity.getEmergencyContact().emergencyContactPhone() != null)
                ? entity.getEmergencyContact().emergencyContactPhone() : "";

        return new PatientResource(
                entity.getId(),
                entity.getDni().dni(),
                entity.getName().firstName(),
                entity.getName().lastName(),
                entity.getEmail().address(),
                entity.getHomeAddress().homeAddress(),
                entity.getBirthday().birthday().toString(),
                phone,
                allergies,
                systemicConditions,
                emergencyContactName,
                emergencyContactPhone
        );
    }
}
