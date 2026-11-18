package com.platform.dentify.patientattention.domain.services;

import com.platform.dentify.patientattention.domain.model.aggregates.Treatment;
import com.platform.dentify.patientattention.domain.model.queries.GetAllTreatmentsByUserIdQuery;
import com.platform.dentify.patientattention.domain.model.queries.GetTreatmentByIdQuery;

import java.util.List;
import java.util.Optional;

public interface TreatmentQueryService {
    List<Treatment> handle(GetAllTreatmentsByUserIdQuery query);
    Optional<Treatment> handle(GetTreatmentByIdQuery query);
}
