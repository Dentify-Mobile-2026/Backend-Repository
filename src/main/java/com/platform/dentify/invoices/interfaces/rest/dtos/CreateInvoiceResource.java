package com.platform.dentify.invoices.interfaces.rest.dtos;

import java.math.BigDecimal;

/**
 * Cuerpo de {@code POST /api/v1/invoices}. {@code amount} e {@code initialPayment} admiten
 * número JSON o texto decimal ("120.50"). {@code initialPayment} es opcional: si no se envía
 * se cobra el total. Las validaciones viven en {@code CreateInvoiceCommand}, no aquí, para
 * que un dato inválido se responda como 400 con su mensaje.
 */
public record CreateInvoiceResource(BigDecimal amount, Long appointmentId, Long paymentMethodId,
                                    BigDecimal initialPayment) {
}
