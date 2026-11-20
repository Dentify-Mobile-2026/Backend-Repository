package com.platform.dentify.invoices.interfaces.rest.dtos;

import java.math.BigDecimal;

/**
 * Cuerpo de {@code POST /api/v1/invoices/{invoiceId}/payments}. {@code amount} admite número
 * JSON o texto decimal. {@code requestId} (p. ej. un UUID v4 generado por el cliente)
 * identifica la operación: reenviarlo con los mismos datos no duplica el abono.
 */
public record RegisterInvoicePaymentResource(BigDecimal amount, Long paymentMethodId, String notes,
                                             String requestId) {
}
