package com.platform.dentify.patientattention.interfaces.rest;

import com.platform.dentify.patientattention.domain.model.commands.CreateTreatmentCommand;
import com.platform.dentify.patientattention.domain.model.commands.DeleteTreatmentCommand;
import com.platform.dentify.patientattention.domain.model.commands.UpdateTreatmentCommand;
import com.platform.dentify.patientattention.domain.model.queries.GetAllTreatmentsByUserIdQuery;
import com.platform.dentify.patientattention.domain.model.queries.GetTreatmentByIdQuery;
import com.platform.dentify.patientattention.domain.services.TreatmentCommandService;
import com.platform.dentify.patientattention.domain.services.TreatmentQueryService;
import com.platform.dentify.patientattention.interfaces.rest.assemblers.CreateTreatmentCommandFromResourceAssembler;
import com.platform.dentify.patientattention.interfaces.rest.assemblers.TreatmentResourceFromEntityAssembler;
import com.platform.dentify.patientattention.interfaces.rest.assemblers.UpdateTreatmentCommandFromResourceAssembler;
import com.platform.dentify.patientattention.interfaces.rest.dtos.CreateTreatmentResource;
import com.platform.dentify.patientattention.interfaces.rest.dtos.TreatmentResource;
import com.platform.dentify.patientattention.interfaces.rest.dtos.UpdateTreatmentResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "api/v1/treatments", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Treatments", description = "Dental Treatments Catalog Endpoint")
public class TreatmentController {

    private final TreatmentCommandService treatmentCommandService;
    private final TreatmentQueryService treatmentQueryService;

    public TreatmentController(
            TreatmentCommandService treatmentCommandService,
            TreatmentQueryService treatmentQueryService
    ) {
        this.treatmentCommandService = treatmentCommandService;
        this.treatmentQueryService = treatmentQueryService;
    }

    @PostMapping
    @Operation(summary = "Create a dental treatment", description = "Create a new treatment in the catalog")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Treatment created"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "409", description = "Treatment already exists")
    })
    public ResponseEntity<?> createTreatment(@RequestBody CreateTreatmentResource resource) {
        try {
            CreateTreatmentCommand command = CreateTreatmentCommandFromResourceAssembler.toCommandFromResource(resource);
            Long treatmentId = treatmentCommandService.handle(command);
            var created = treatmentQueryService.handle(new GetTreatmentByIdQuery(treatmentId));
            if (created.isPresent()) {
                return ResponseEntity.status(HttpStatus.CREATED)
                        .body(TreatmentResourceFromEntityAssembler.toResourceFromEntity(created.get()));
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", treatmentId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Error: " + e.getMessage()));
        }
    }

    @GetMapping
    @Operation(summary = "Get all treatments for current user", description = "Returns all dental treatments in the catalog")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Treatments retrieved")
    })
    public ResponseEntity<List<TreatmentResource>> getAllTreatments() {
        var treatments = treatmentQueryService.handle(new GetAllTreatmentsByUserIdQuery());
        var resources = treatments.stream()
                .map(TreatmentResourceFromEntityAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(resources);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get treatment by ID", description = "Returns a single treatment by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Treatment found"),
            @ApiResponse(responseCode = "404", description = "Treatment not found")
    })
    public ResponseEntity<TreatmentResource> getTreatmentById(@PathVariable Long id) {
        var treatment = treatmentQueryService.handle(new GetTreatmentByIdQuery(id));
        if (treatment.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(TreatmentResourceFromEntityAssembler.toResourceFromEntity(treatment.get()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update treatment", description = "Update an existing treatment by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Updated treatment"),
            @ApiResponse(responseCode = "404", description = "Treatment not found")
    })
    public ResponseEntity<?> updateTreatment(
            @PathVariable Long id,
            @RequestBody UpdateTreatmentResource resource
    ) {
        try {
            UpdateTreatmentCommand command = UpdateTreatmentCommandFromResourceAssembler.toCommandFromResource(resource, id);
            var updated = treatmentCommandService.handle(command);
            if (updated.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            return ResponseEntity.ok(TreatmentResourceFromEntityAssembler.toResourceFromEntity(updated.get()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a treatment", description = "Delete a treatment by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Deleted treatment"),
            @ApiResponse(responseCode = "404", description = "Treatment not found")
    })
    public ResponseEntity<?> deleteTreatment(@PathVariable Long id) {
        try {
            treatmentCommandService.handle(new DeleteTreatmentCommand(id));
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }
}
