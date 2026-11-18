package com.platform.dentify.patientattention.interfaces.rest.assemblers;

import com.platform.dentify.patientattention.domain.model.aggregates.Appointment;
import com.platform.dentify.patientattention.interfaces.rest.dtos.AppointmentResource;

public class AppointmentResourceFromEntityAssembler {
    public static AppointmentResource toResourceFromEntity(Appointment appointment) {
        String patientPhone = (appointment.getPatient().getPhone() != null
                && appointment.getPatient().getPhone().phone() != null)
                ? appointment.getPatient().getPhone().phone()
                : "";

        Long treatmentId = appointment.getTreatment() != null ? appointment.getTreatment().getId() : null;
        String treatmentName = appointment.getTreatment() != null ? appointment.getTreatment().getName() : "";
        String clinicalNotes = appointment.getClinicalNotes() != null ? appointment.getClinicalNotes() : "";

        return new AppointmentResource(
                appointment.getId(),
                appointment.getPatient().getName().FullName(),
                appointment.getPatient().getDni().dni(),
                patientPhone,
                appointment.getAppointmentDate(),
                appointment.getReason(),
                appointment.getCompleted(),
                appointment.resolveStatus().name(),
                clinicalNotes,
                treatmentId,
                treatmentName,
                appointment.getDuration(),
                appointment.getCreatedAt()
        );
    }
}
