package com.platform.dentify.patientattention.application.internal;

import com.platform.dentify.iam.infrastructure.security.AuthenticatedUserProvider;
import com.platform.dentify.patientattention.domain.model.aggregates.Patient;
import com.platform.dentify.patientattention.domain.model.aggregates.TreatmentPlan;
import com.platform.dentify.patientattention.domain.model.commands.*;
import com.platform.dentify.patientattention.domain.model.queries.GetTreatmentPlansByPatientQuery;
import com.platform.dentify.patientattention.domain.services.TreatmentPlanCommandService;
import com.platform.dentify.patientattention.domain.services.TreatmentPlanQueryService;
import com.platform.dentify.patientattention.infrastructure.repositories.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
@Transactional
public class TreatmentPlanServiceImpl implements TreatmentPlanCommandService, TreatmentPlanQueryService {
    private final TreatmentPlanRepository plans;
    private final PatientRepository patients;
    private final TreatmentRepository treatments;
    private final AuthenticatedUserProvider auth;

    public TreatmentPlanServiceImpl(TreatmentPlanRepository plans, PatientRepository patients,
                                    TreatmentRepository treatments, AuthenticatedUserProvider auth) {
        this.plans = plans;
        this.patients = patients;
        this.treatments = treatments;
        this.auth = auth;
    }

    public TreatmentPlan handle(CreateTreatmentPlanCommand command) {
        Long userId = auth.getCurrentUserId();
        var patient = patient(command.patientId(), userId);
        if (command.items() == null || command.items().size() < 2 || command.items().size() > 100) {
            throw new IllegalArgumentException("A plan requires between 2 and 100 treatment items");
        }
        var plan = new TreatmentPlan(patient, command.title(), command.notes());
        for (var item : command.items()) {
            var treatment = treatments.findByIdAndUser_Id(item.treatmentId(), userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Treatment not found"));
            if (!treatment.isActive()) throw new IllegalArgumentException("Inactive treatments cannot be quoted");
            plan.addItem(treatment, item.toothNumber(), item.agreedPrice());
        }
        return plans.save(plan);
    }

    @Transactional(readOnly = true)
    public List<TreatmentPlan> handle(GetTreatmentPlansByPatientQuery query) {
        Long userId = auth.getCurrentUserId();
        patient(query.patientId(), userId);
        return plans.findAllByPatient_IdAndUser_IdOrderByCreatedAtDesc(query.patientId(), userId);
    }

    public TreatmentPlan handle(UpdateTreatmentPlanStatusCommand command) {
        var plan = plan(command.planId());
        if (!plan.getPatient().getId().equals(command.patientId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Treatment plan not found");
        }
        plan.changeStatus(command.status());
        return plans.save(plan);
    }

    public TreatmentPlan handle(UpdateTreatmentPlanItemStatusCommand command) {
        var plan = plan(command.planId());
        plan.changeItemStatus(command.itemId(), command.status());
        return plans.save(plan);
    }

    private Patient patient(Long id, Long userId) {
        return patients.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));
    }

    private TreatmentPlan plan(Long id) {
        return plans.findByIdAndUser_Id(id, auth.getCurrentUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Treatment plan not found"));
    }
}
