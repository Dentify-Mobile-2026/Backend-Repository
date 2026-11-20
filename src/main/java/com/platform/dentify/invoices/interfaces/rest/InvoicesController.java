package com.platform.dentify.invoices.interfaces.rest;

import com.platform.dentify.invoices.domain.model.aggregates.Invoice;
import com.platform.dentify.invoices.domain.model.commands.CreateInvoiceCommand;
import com.platform.dentify.invoices.domain.model.queries.GetAllInvoicesByUserIdQuery;
import com.platform.dentify.invoices.domain.model.queries.GetInvoiceByAppointmentIdQuery;
import com.platform.dentify.invoices.domain.model.valueobjects.InvoiceStatus;
import com.platform.dentify.invoices.domain.services.InvoiceCommandService;
import com.platform.dentify.invoices.domain.services.InvoiceQueryService;
import com.platform.dentify.invoices.interfaces.rest.assemblers.CreateInvoiceCommandFromResourceAssembler;
import com.platform.dentify.invoices.interfaces.rest.assemblers.InvoiceResourceFromEntityAssembler;
import com.platform.dentify.invoices.interfaces.rest.dtos.CreateInvoiceResource;
import com.platform.dentify.invoices.interfaces.rest.dtos.InvoiceResource;
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

/**
 * Los errores se traducen en {@link InvoicesExceptionHandler}: 400 (datos inválidos),
 * 404 (la cita no existe o no es del usuario) y 409 (la cita ya tiene comprobante).
 */
@RestController
@RequestMapping(value = "api/v1/invoices", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Invoices", description = "Invoices Endpoint")
public class InvoicesController {

    private final InvoiceCommandService invoiceCommandService;
    private final InvoiceQueryService invoiceQueryService;

    public InvoicesController(InvoiceCommandService invoiceCommandService, InvoiceQueryService invoiceQueryService) {
        this.invoiceCommandService = invoiceCommandService;
        this.invoiceQueryService = invoiceQueryService;
    }

    @PostMapping
    @Operation(summary = "Create an invoice",
            description = "Creates the invoice of an appointment. The amount accepts a JSON number or a decimal string "
                    + "(\"120.50\"), must be greater than zero and have at most 2 decimals. "
                    + "Creating an invoice does not change the appointment's clinical status.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Invoice created"),
            @ApiResponse(responseCode = "400", description = "Invalid amount, missing field, invalid JSON or unknown payment method"),
            @ApiResponse(responseCode = "404", description = "Appointment not found or not owned by the current user"),
            @ApiResponse(responseCode = "409", description = "The appointment already has an invoice")
    })
    public ResponseEntity<InvoiceResource> createInvoice(@RequestBody CreateInvoiceResource resource) {
        CreateInvoiceCommand command = CreateInvoiceCommandFromResourceAssembler.toCommandFromResource(resource);
        Invoice invoice = invoiceCommandService.handle(command)
                .orElseThrow(() -> new IllegalStateException("The invoice could not be created"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(InvoiceResourceFromEntityAssembler.toResourceFromEntity(invoice));
    }

    @GetMapping("/{appointmentId}")
    @Operation(summary = "Get an invoice by appointmentId", description = "Retrieve an invoice using the appointment ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Invoice found"),
            @ApiResponse(responseCode = "404", description = "Appointment or invoice not found")
    })
    public ResponseEntity<?> getInvoiceByAppointmentId(@PathVariable Long appointmentId) {
        return invoiceQueryService.handle(new GetInvoiceByAppointmentIdQuery(appointmentId))
                .<ResponseEntity<?>>map(invoice -> ResponseEntity.ok(InvoiceResourceFromEntityAssembler.toResourceFromEntity(invoice)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "La cita " + appointmentId + " no tiene comprobante")));
    }

    @GetMapping("")
    @Operation(summary = "Get all invoices for current user",
            description = "Returns all invoices linked to the current user, optionally filtered by billing status "
                    + "(PENDING, PARTIAL, PAID). Historical invoices count as PAID.")
    public ResponseEntity<List<InvoiceResource>> getAllInvoicesForCurrentUser(
            @RequestParam(name = "status", required = false) InvoiceStatus status) {
        List<Invoice> invoices = invoiceQueryService.handle(new GetAllInvoicesByUserIdQuery());
        var resources = invoices.stream()
                .filter(invoice -> status == null || invoice.getStatus() == status)
                .map(InvoiceResourceFromEntityAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(resources);
    }
}
