package com.platform.dentify.invoices.interfaces.rest.dtos;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Representación pública de un comprobante.
 * <p>
 * Los campos originales se mantienen en el mismo orden y con el mismo significado
 * ({@code amount} sigue siendo el total, como double). Se añaden al final
 * {@code totalAmount} (decimal exacto), {@code paymentMethodId} y {@code paymentMethodName}.
 */
public record InvoiceResource(
        Long id,
        Long appointmentId,
        String patientName,
        String dni,
        String email,
        Double amount,
        LocalDateTime createdAt,
        BigDecimal totalAmount,
        Long paymentMethodId,
        String paymentMethodName
) {
}
