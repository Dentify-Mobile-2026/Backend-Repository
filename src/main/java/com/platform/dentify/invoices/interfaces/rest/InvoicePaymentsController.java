package com.platform.dentify.invoices.interfaces.rest;

import com.platform.dentify.invoices.domain.model.queries.GetInvoicePaymentsQuery;
import com.platform.dentify.invoices.domain.services.InvoicePaymentCommandService;
import com.platform.dentify.invoices.domain.services.InvoiceQueryService;
import com.platform.dentify.invoices.interfaces.rest.assemblers.InvoicePaymentResourceAssembler;
import com.platform.dentify.invoices.interfaces.rest.assemblers.InvoiceResourceFromEntityAssembler;
import com.platform.dentify.invoices.interfaces.rest.dtos.InvoicePaymentResource;
import com.platform.dentify.invoices.interfaces.rest.dtos.InvoiceResource;
import com.platform.dentify.invoices.interfaces.rest.dtos.RegisterInvoicePaymentResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Abonos de un comprobante. Los errores se traducen en {@link InvoicesExceptionHandler}.
 */
@RestController
@RequestMapping(value = "/api/v1/invoices/{invoiceId}/payments", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Invoice Payments", description = "Partial payments of an invoice")
public class InvoicePaymentsController {

    private final InvoicePaymentCommandService invoicePaymentCommandService;
    private final InvoiceQueryService invoiceQueryService;

    public InvoicePaymentsController(InvoicePaymentCommandService invoicePaymentCommandService,
                                     InvoiceQueryService invoiceQueryService) {
        this.invoicePaymentCommandService = invoicePaymentCommandService;
        this.invoiceQueryService = invoiceQueryService;
    }

    @PostMapping
    @Operation(summary = "Register a partial payment",
            description = "Registers a payment on the invoice. requestId identifies the client operation: repeating it "
                    + "with the same data returns 200 with the current invoice and does not create another payment.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Payment registered; returns the updated invoice"),
            @ApiResponse(responseCode = "200", description = "Idempotent replay of an already registered payment"),
            @ApiResponse(responseCode = "400", description = "Invalid amount, missing field or unknown payment method"),
            @ApiResponse(responseCode = "404", description = "Invoice not found or not owned by the current user"),
            @ApiResponse(responseCode = "409", description = "Payment exceeds the pending balance, or requestId reused with different data")
    })
    public ResponseEntity<InvoiceResource> registerPayment(@PathVariable Long invoiceId,
                                                           @RequestBody RegisterInvoicePaymentResource resource) {
        var result = invoicePaymentCommandService.handle(InvoicePaymentResourceAssembler.toCommand(invoiceId, resource));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(InvoiceResourceFromEntityAssembler.toResourceFromEntity(result.invoice()));
    }

    @GetMapping
    @Operation(summary = "List payments of an invoice", description = "Payments ordered from oldest to newest; paidAt is UTC")
    public ResponseEntity<List<InvoicePaymentResource>> getPayments(@PathVariable Long invoiceId) {
        var resources = invoiceQueryService.handle(new GetInvoicePaymentsQuery(invoiceId)).stream()
                .map(InvoicePaymentResourceAssembler::toResource)
                .toList();
        return ResponseEntity.ok(resources);
    }
}
