package com.platform.dentify.patientattention.domain.services;

import com.platform.dentify.patientattention.domain.model.aggregates.TreatmentPlan;
import com.platform.dentify.patientattention.domain.model.commands.*;

public interface TreatmentPlanCommandService {
    TreatmentPlan handle(CreateTreatmentPlanCommand command);
    TreatmentPlan handle(UpdateTreatmentPlanStatusCommand command);
    TreatmentPlan handle(UpdateTreatmentPlanItemStatusCommand command);
}
