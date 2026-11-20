package com.platform.dentify.invoices.domain.model.valueobjects;

/**
 * Estado de cobro de un comprobante. Siempre coherente con los montos:
 * PENDING ⇔ pagado = 0 · PARTIAL ⇔ 0 &lt; pagado &lt; total · PAID ⇔ pagado = total.
 */
public enum InvoiceStatus {
    PENDING,
    PARTIAL,
    PAID
}
