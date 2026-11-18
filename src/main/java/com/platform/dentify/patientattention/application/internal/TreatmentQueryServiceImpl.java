package com.platform.dentify.patientattention.application.internal;

import com.platform.dentify.iam.infrastructure.security.AuthenticatedUserProvider;
import com.platform.dentify.patientattention.domain.model.aggregates.Treatment;
import com.platform.dentify.patientattention.domain.model.queries.GetAllTreatmentsByUserIdQuery;
import com.platform.dentify.patientattention.domain.model.queries.GetTreatmentByIdQuery;
import com.platform.dentify.patientattention.domain.services.TreatmentQueryService;
import com.platform.dentify.patientattention.infrastructure.repositories.TreatmentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TreatmentQueryServiceImpl implements TreatmentQueryService {

    private final TreatmentRepository treatmentRepository;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    public TreatmentQueryServiceImpl(
            TreatmentRepository treatmentRepository,
            AuthenticatedUserProvider authenticatedUserProvider
    ) {
        this.treatmentRepository = treatmentRepository;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    @Override
    public List<Treatment> handle(GetAllTreatmentsByUserIdQuery query) {
        Long userId = authenticatedUserProvider.getCurrentUserId();
        return treatmentRepository.findAllByUser_IdOrderByNameAsc(userId);
    }

    @Override
    public Optional<Treatment> handle(GetTreatmentByIdQuery query) {
        Long userId = authenticatedUserProvider.getCurrentUserId();
        return treatmentRepository.findByIdAndUser_Id(query.id(), userId);
    }
}
