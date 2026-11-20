package com.platform.dentify.patientattention.domain.services;

import com.platform.dentify.patientattention.domain.model.aggregates.TreatmentPlan;
import com.platform.dentify.patientattention.domain.model.queries.GetTreatmentPlansByPatientQuery;
import java.util.List;

public interface TreatmentPlanQueryService {
    List<TreatmentPlan> handle(GetTreatmentPlansByPatientQuery query);
}
