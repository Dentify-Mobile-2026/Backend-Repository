package com.platform.dentify.patientattention.interfaces.rest;

import com.platform.dentify.patientattention.domain.services.BusinessHoursService;
import com.platform.dentify.patientattention.interfaces.rest.assemblers.BusinessHoursResourceAssembler;
import com.platform.dentify.patientattention.interfaces.rest.dtos.BusinessHoursResource;
import com.platform.dentify.patientattention.interfaces.rest.dtos.UpdateBusinessHoursResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Horario de atención del consultorio autenticado (US09). */
@RestController
@RequestMapping(value = "api/v1/business-hours", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Business hours", description = "Opening hours of the dental office")
public class BusinessHoursController {

    private final BusinessHoursService businessHoursService;

    public BusinessHoursController(BusinessHoursService businessHoursService) {
        this.businessHoursService = businessHoursService;
    }

    @GetMapping
    @Operation(summary = "Get business hours",
            description = "Returns the office hours; configured=false means none were saved and no appointment is restricted yet")
    public ResponseEntity<BusinessHoursResource> getBusinessHours() {
        return ResponseEntity.ok(BusinessHoursResourceAssembler.toResource(businessHoursService.getForCurrentUser()));
    }

    @PutMapping
    @Operation(summary = "Save business hours",
            description = "Saves the office hours; they apply to appointments created or rescheduled afterwards")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Business hours saved"),
            @ApiResponse(responseCode = "400", description = "Invalid days or hours")
    })
    public ResponseEntity<?> saveBusinessHours(@RequestBody UpdateBusinessHoursResource resource) {
        try {
            var command = BusinessHoursResourceAssembler.toCommand(resource);
            var days = businessHoursService.handle(command);
            return ResponseEntity.ok(BusinessHoursResourceAssembler.toResource(days));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }
}
