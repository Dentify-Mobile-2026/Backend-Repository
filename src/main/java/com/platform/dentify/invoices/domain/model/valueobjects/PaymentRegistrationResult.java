package com.platform.dentify.invoices.domain.model.valueobjects;

import com.platform.dentify.invoices.domain.model.aggregates.Invoice;

/**
 * Resultado de registrar un abono: el comprobante actualizado y si el abono se creó ahora
 * ({@code created = true}) o era una repetición idempotente de una operación ya confirmada.
 */
public record PaymentRegistrationResult(Invoice invoice, boolean created) {
}
