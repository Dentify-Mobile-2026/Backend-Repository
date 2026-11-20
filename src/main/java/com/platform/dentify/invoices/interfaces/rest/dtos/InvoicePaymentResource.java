package com.platform.dentify.invoices.interfaces.rest.dtos;

import java.math.BigDecimal;
import java.time.Instant;

/** Abono de un comprobante. {@code paidAt} es un instante ISO-8601 en UTC. */
public record InvoicePaymentResource(Long id, BigDecimal amount, Long paymentMethodId, String paymentMethodName,
                                     String notes, Instant paidAt) {
}
