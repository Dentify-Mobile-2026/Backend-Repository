package com.platform.dentify.patientattention.interfaces.rest;

import com.platform.dentify.patientattention.domain.model.commands.*;
import com.platform.dentify.patientattention.domain.model.queries.GetTreatmentPlansByPatientQuery;
import com.platform.dentify.patientattention.domain.model.valueobjects.*;
import com.platform.dentify.patientattention.domain.services.*;
import com.platform.dentify.patientattention.interfaces.rest.assemblers.TreatmentPlanResourceAssembler;
import com.platform.dentify.patientattention.interfaces.rest.dtos.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class TreatmentPlanController {
    private final TreatmentPlanCommandService commands;
    private final TreatmentPlanQueryService queries;

    public TreatmentPlanController(TreatmentPlanCommandService commands, TreatmentPlanQueryService queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @GetMapping("/patients/{patientId}/treatment-plans")
    public List<TreatmentPlanResource> list(@PathVariable Long patientId) {
        return queries.handle(new GetTreatmentPlansByPatientQuery(patientId)).stream()
                .map(TreatmentPlanResourceAssembler::toResource).toList();
    }

    @PostMapping("/patients/{patientId}/treatment-plans")
    public ResponseEntity<TreatmentPlanResource> create(@PathVariable Long patientId,
                                                       @Valid @RequestBody CreateTreatmentPlanResource resource) {
        var plan = commands.handle(TreatmentPlanResourceAssembler.toCommand(patientId, resource));
        return ResponseEntity.status(201).body(TreatmentPlanResourceAssembler.toResource(plan));
    }

    public record PlanStatusResource(@NotNull TreatmentPlanStatus status) {}
    public record ItemStatusResource(@NotNull TreatmentPlanItemStatus status) {}

    @PutMapping("/patients/{patientId}/treatment-plans/{planId}/status")
    public TreatmentPlanResource status(@PathVariable Long patientId, @PathVariable Long planId,
                                       @Valid @RequestBody PlanStatusResource resource) {
        return TreatmentPlanResourceAssembler.toResource(commands.handle(
                new UpdateTreatmentPlanStatusCommand(patientId, planId, resource.status())));
    }

    @PutMapping("/treatment-plans/{planId}/items/{itemId}/status")
    public TreatmentPlanResource itemStatus(@PathVariable Long planId, @PathVariable Long itemId,
                                            @Valid @RequestBody ItemStatusResource resource) {
        return TreatmentPlanResourceAssembler.toResource(commands.handle(
                new UpdateTreatmentPlanItemStatusCommand(planId, itemId, resource.status())));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> invalid(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(Map.of("error", error.getMessage()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<?> concurrentUpdate() {
        return ResponseEntity.status(409).body(Map.of("error", "The plan changed. Reload and try again."));
    }
}
