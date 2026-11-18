package com.platform.dentify.patientattention.interfaces.rest.assemblers;

import com.platform.dentify.patientattention.domain.model.commands.UpdateAppointmentStatusCommand;
import com.platform.dentify.patientattention.interfaces.rest.dtos.UpdateAppointmentStatusResource;

public class UpdateAppointmentStatusCommandFromResourceAssembler {
    public static UpdateAppointmentStatusCommand toCommandFromResource(Long id, UpdateAppointmentStatusResource resource) {
        return new UpdateAppointmentStatusCommand(
                id,
                resource.status(),
                resource.clinicalNotes()
        );
    }
}
