package com.platform.dentify.invoices.interfaces.rest.dtos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Representación pública de un comprobante.
 * <p>
 * Los campos originales se mantienen en el mismo orden y con el mismo significado
 * ({@code amount} sigue siendo el total, como double). Sprint 1 añadió {@code totalAmount},
 * {@code paymentMethodId} y {@code paymentMethodName}; Sprint 2 añade al final los saldos:
 * {@code paidAmount}, {@code pendingBalance}, {@code status} (PENDING/PARTIAL/PAID),
 * {@code legacy} (comprobante histórico cobrado en su totalidad, sin abonos),
 * {@code paymentsCount} y {@code paymentMethods} (métodos distintos usados en los abonos).
 * {@code paymentMethodId}/{@code paymentMethodName} solo se informan si se usó un único método.
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
        String paymentMethodName,
        BigDecimal paidAmount,
        BigDecimal pendingBalance,
        String status,
        boolean legacy,
        int paymentsCount,
        List<String> paymentMethods
) {
}
