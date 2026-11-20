package com.platform.dentify.invoices.domain.exceptions;

import java.math.BigDecimal;

/** El abono supera el saldo pendiente del comprobante. */
public class InvoiceOverpaymentException extends RuntimeException {
    public InvoiceOverpaymentException(BigDecimal amount, BigDecimal pendingBalance) {
        super("El abono (S/. " + amount.toPlainString() + ") excede el saldo pendiente (S/. "
                + pendingBalance.toPlainString() + ")");
    }
}
