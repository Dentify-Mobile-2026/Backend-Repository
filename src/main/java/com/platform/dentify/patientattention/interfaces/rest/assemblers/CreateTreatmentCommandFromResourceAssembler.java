package com.platform.dentify.patientattention.interfaces.rest.assemblers;

import com.platform.dentify.patientattention.domain.model.commands.CreateTreatmentCommand;
import com.platform.dentify.patientattention.interfaces.rest.dtos.CreateTreatmentResource;

public class CreateTreatmentCommandFromResourceAssembler {
    public static CreateTreatmentCommand toCommandFromResource(CreateTreatmentResource resource) {
        return new CreateTreatmentCommand(
                resource.name(),
                resource.description(),
                resource.category(),
                resource.price(),
                resource.estimatedDurationMinutes()
        );
    }
}
