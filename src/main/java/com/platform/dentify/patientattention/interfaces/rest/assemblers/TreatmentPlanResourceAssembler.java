package com.platform.dentify.patientattention.interfaces.rest.assemblers;

import com.platform.dentify.patientattention.domain.model.aggregates.TreatmentPlan;
import com.platform.dentify.patientattention.domain.model.commands.CreateTreatmentPlanCommand;
import com.platform.dentify.patientattention.interfaces.rest.dtos.CreateTreatmentPlanResource;
import com.platform.dentify.patientattention.interfaces.rest.dtos.TreatmentPlanResource;

public final class TreatmentPlanResourceAssembler {
    private TreatmentPlanResourceAssembler() {}

    public static CreateTreatmentPlanCommand toCommand(Long patientId, CreateTreatmentPlanResource resource) {
        return new CreateTreatmentPlanCommand(patientId, resource.title(), resource.notes(), resource.items().stream()
                .map(i -> new CreateTreatmentPlanCommand.Item(i.treatmentId(), i.toothNumber(), i.agreedPrice())).toList());
    }

    public static TreatmentPlanResource toResource(TreatmentPlan plan) {
        return new TreatmentPlanResource(plan.getId(), plan.getPatient().getId(), plan.getTitle(), plan.getStatus().name(),
                plan.getTotalAmount(), plan.getNotes(), plan.getCompletedItems(), plan.getItems().size(),
                plan.getProgressPercentage(), plan.getCreatedAt(), plan.getUpdatedAt(), plan.getItems().stream()
                .map(i -> new TreatmentPlanResource.Item(i.getId(), i.getTreatment().getId(), i.getTreatmentName(),
                        i.getToothNumber(), i.getAgreedPrice(), i.getItemStatus().name())).toList());
    }
}
