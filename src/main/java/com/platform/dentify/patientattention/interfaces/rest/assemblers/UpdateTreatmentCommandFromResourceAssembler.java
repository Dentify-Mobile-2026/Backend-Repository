package com.platform.dentify.patientattention.interfaces.rest.assemblers;

import com.platform.dentify.patientattention.domain.model.commands.UpdateTreatmentCommand;
import com.platform.dentify.patientattention.interfaces.rest.dtos.UpdateTreatmentResource;

public class UpdateTreatmentCommandFromResourceAssembler {
    public static UpdateTreatmentCommand toCommandFromResource(UpdateTreatmentResource resource, Long id) {
        return new UpdateTreatmentCommand(
                id,
                resource.name(),
                resource.description(),
                resource.category(),
                resource.price(),
                resource.estimatedDurationMinutes(),
                resource.isActive()
        );
    }
}
