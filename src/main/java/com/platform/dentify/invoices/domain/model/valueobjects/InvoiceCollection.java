package com.platform.dentify.invoices.domain.model.valueobjects;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Dinero efectivamente cobrado sobre un comprobante en un instante concreto: un abono o,
 * para comprobantes históricos sin abonos, el cobro total registrado al emitirlos.
 */
public record InvoiceCollection(Long invoiceId, BigDecimal amount, Instant collectedAt) {
}
