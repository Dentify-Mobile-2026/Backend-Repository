package com.platform.dentify.invoices.interfaces.rest;

import com.platform.dentify.invoices.domain.model.queries.GetAllPaymentMethodsQuery;
import com.platform.dentify.invoices.domain.services.PaymentMethodQueryService;
import com.platform.dentify.invoices.interfaces.rest.assemblers.PaymentMethodResourceFromEntityAssembler;
import com.platform.dentify.invoices.interfaces.rest.dtos.PaymentMethodResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/payment-methods", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Payment Methods", description = "Payment methods catalog")
public class PaymentMethodsController {

    private final PaymentMethodQueryService paymentMethodQueryService;

    public PaymentMethodsController(PaymentMethodQueryService paymentMethodQueryService) {
        this.paymentMethodQueryService = paymentMethodQueryService;
    }

    @GetMapping
    @Operation(summary = "List payment methods",
            description = "Returns the payment methods catalog ordered by id. Clients must use these ids; they are not fixed.")
    public ResponseEntity<List<PaymentMethodResource>> getAllPaymentMethods() {
        var resources = paymentMethodQueryService.handle(new GetAllPaymentMethodsQuery())
                .stream()
                .map(PaymentMethodResourceFromEntityAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(resources);
    }
}
