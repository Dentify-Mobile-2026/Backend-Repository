package com.platform.dentify.patientattention.interfaces.rest.assemblers;

import com.platform.dentify.patientattention.domain.model.aggregates.Treatment;
import com.platform.dentify.patientattention.interfaces.rest.dtos.TreatmentResource;

public class TreatmentResourceFromEntityAssembler {
    public static TreatmentResource toResourceFromEntity(Treatment entity) {
        return new TreatmentResource(
                entity.getId(),
                entity.getName(),
                entity.getDescription() != null ? entity.getDescription() : "",
                entity.getCategory().name(),
                entity.getPrice(),
                entity.getEstimatedDurationMinutes(),
                entity.isActive()
        );
    }
}
